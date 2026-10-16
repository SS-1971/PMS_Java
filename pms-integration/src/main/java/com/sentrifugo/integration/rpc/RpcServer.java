package com.sentrifugo.integration.rpc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java-side counterpart to the Python suite's shared Direct Reply-To RPC server
 * scaffolding ({@code Sentrifugo-IAM-Admin-BE/src/rpc/server.py}'s {@code RpcServer}):
 * one durable queue, consumed by one handler, replying with the envelope every
 * Python RPC client already expects:
 * {@code {"correlation_id": "...", "data": <result | null>, "error": <string | null>}}.
 *
 * <p>This is a plain object, not a Spring bean — exactly like the Python version,
 * where each procedure (e.g. {@code rpc/permission_check.py}) builds its own
 * module-level {@code RpcServer(queue_name, handler)} instance. A procedure
 * exposed from PMS does the same here: construct one {@code RpcServer} (typically
 * from a {@code @Component}'s constructor, injecting {@link ConnectionFactory},
 * {@link RabbitTemplate} and {@link JsonMapper}), {@link #start()} it on
 * {@code @PostConstruct} / an {@code ApplicationReadyEvent} listener, and
 * {@link #stop()} it on {@code @PreDestroy}:
 *
 * <pre>{@code
 * @Component
 * public class GoalTemplateLookupRpc {
 *     private final RpcServer server;
 *
 *     public GoalTemplateLookupRpc(ConnectionFactory cf, RabbitTemplate rt, JsonMapper mapper,
 *                                   @Value("${pms.rpc.environment:production}") String env) {
 *         this.server = RpcServer.forProcedure(cf, rt, mapper, env, "pms",
 *                 "goal_template_for_designation", this::handle, 10);
 *     }
 *
 *     private Object handle(JsonNode payload) {
 *         // decode payload, run the lookup, return whatever should be `data`.
 *         // Throwing becomes an `error` envelope, not a hang.
 *     }
 *
 *     @PostConstruct void start() { server.start(); }
 *     @PreDestroy void stop() { server.stop(); }
 * }
 * }</pre>
 *
 * <p>The same three guarantees the Python version documents apply here:
 * <ul>
 *   <li><b>Always reply.</b> A handler that throws, an undecodable body, or a
 *       missing broker on the reply path all still produce — or attempt — an
 *       envelope, rather than leaving the caller to its timeout.</li>
 *   <li><b>Distinguish failure from emptiness.</b> {@code error} is how a caller
 *       tells "this service blew up" from "this service says there is nothing".</li>
 *   <li><b>Survive a broker-down boot.</b> {@link #start()} retries queue
 *       declaration and consumer registration in the background until the
 *       broker is reachable, instead of failing application startup.</li>
 * </ul>
 */
@Slf4j
public class RpcServer {

    private static final long START_RETRY_SECONDS = 5;

    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final String queueName;
    private final RpcHandler handler;
    private final int prefetch;
    private final String logName;

    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private ScheduledExecutorService starter;
    private volatile SimpleMessageListenerContainer container;

    public RpcServer(ConnectionFactory connectionFactory, RabbitTemplate rabbitTemplate, JsonMapper jsonMapper,
                      String queueName, RpcHandler handler, int prefetch, String logName) {
        this.connectionFactory = connectionFactory;
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
        this.queueName = queueName;
        this.handler = handler;
        this.prefetch = prefetch;
        this.logName = logName;
    }

    /** Builds the queue name the same way {@link ServiceRpcClient} builds its routing key. */
    public static RpcServer forProcedure(ConnectionFactory connectionFactory, RabbitTemplate rabbitTemplate,
                                          JsonMapper jsonMapper, String environment, String service,
                                          String procedure, RpcHandler handler, int prefetch) {
        String queueName = "%s.%s.rpc.%s.queue".formatted(environment, service, procedure);
        return new RpcServer(connectionFactory, rabbitTemplate, jsonMapper, queueName, handler, prefetch, procedure);
    }

    /**
     * Registers the consumer, retrying every {@value #START_RETRY_SECONDS}s in the
     * background while the broker is unreachable — mirrors Python's {@code _run()}
     * loop. Never blocks the calling thread and never fails application startup.
     */
    public void start() {
        stopped.set(false);
        starter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rpc-" + logName + "-starter");
            t.setDaemon(true);
            return t;
        });
        starter.execute(this::tryStart);
    }

    private void tryStart() {
        if (stopped.get()) {
            return;
        }
        try {
            new RabbitAdmin(connectionFactory).declareQueue(new Queue(queueName, true));

            SimpleMessageListenerContainer newContainer = new SimpleMessageListenerContainer(connectionFactory);
            newContainer.setQueueNames(queueName);
            newContainer.setPrefetchCount(prefetch);
            newContainer.setMessageListener(this::handleMessage);
            newContainer.start();

            container = newContainer;
            log.info("RPC {} consumer started queue={}", logName, queueName);
        } catch (Exception e) {
            log.warn("rpc.{}: RabbitMQ not ready, retrying in {}s", logName, START_RETRY_SECONDS);
            if (!stopped.get()) {
                starter.schedule(this::tryStart, START_RETRY_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    public void stop() {
        stopped.set(true);
        if (starter != null) {
            starter.shutdownNow();
        }
        if (container != null) {
            container.stop();
        }
    }

    // ── Message handling — package-private so it is unit-testable without a broker ──

    void handleMessage(Message message) {
        MessageProperties props = message.getMessageProperties();
        String replyTo = props.getReplyTo();
        if (replyTo == null || replyTo.isEmpty()) {
            // No return address; replying is impossible, so drop it.
            log.warn("rpc.{}: message missing reply_to, dropping", logName);
            return;
        }
        String correlationId = props.getCorrelationId();

        Object data = null;
        String error = null;
        try {
            JsonNode payload = jsonMapper.readTree(message.getBody());
            data = handler.handle(payload);
        } catch (Exception e) {
            // An error envelope, NOT an empty result: the caller must be able to
            // retry rather than treat this as an authoritative "nothing".
            log.error("rpc.{}: handler failed correlationId={}", logName, correlationId, e);
            error = e.toString();
        }
        reply(replyTo, correlationId, data, error);
    }

    void reply(String replyTo, String correlationId, Object data, String error) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("correlation_id", correlationId);
        envelope.put("data", data);
        envelope.put("error", error);

        MessageProperties replyProps = new MessageProperties();
        replyProps.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        replyProps.setCorrelationId(correlationId);

        try {
            byte[] body = jsonMapper.writeValueAsBytes(envelope);
            rabbitTemplate.send("", replyTo, new Message(body, replyProps));
        } catch (Exception e) {
            // Nothing further we can do — the caller will fall back to its
            // timeout. Log loudly so a broken reply path is visible.
            log.error("rpc.{}: failed to send reply correlationId={}", logName, correlationId, e);
        }
    }
}
