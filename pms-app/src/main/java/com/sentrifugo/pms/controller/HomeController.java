package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational / diagnostic endpoints. */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/health")
    @Operation(summary = "Service health check",
            description = "Confirms the PMS service is up and able to authenticate the caller.")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.ok("PMS service is running", "PMS service is healthy"));
    }

    @GetMapping("/valkey-test")
    @Operation(summary = "Valkey connectivity test",
            description = "Writes and reads back a test key to verify the Valkey/Redis connection.")
    public ResponseEntity<ApiResponse<String>> testValkey() {
        redisTemplate.opsForValue().set("pms:test", "Valkey connection successful");
        String value = redisTemplate.opsForValue().get("pms:test");
        return ResponseEntity.ok(ApiResponse.ok(value, "Valkey connectivity test completed"));
    }

    @GetMapping("/redis-config")
    @Operation(summary = "Show Redis configuration",
            description = "Returns the configured Redis host, port and url properties for diagnostics.")
    public ResponseEntity<ApiResponse<String>> redisConfig(Environment environment) {
        String host = environment.getProperty("spring.data.redis.host");
        String port = environment.getProperty("spring.data.redis.port");
        String url = environment.getProperty("spring.data.redis.url");
        return ResponseEntity.ok(ApiResponse.ok("host=" + host + ", port=" + port + ", url=" + url,
                "Redis configuration retrieved successfully"));
    }
}
