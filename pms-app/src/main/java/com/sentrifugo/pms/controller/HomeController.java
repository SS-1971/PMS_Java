package com.sentrifugo.pms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    @Autowired
    private StringRedisTemplate redisTemplate;


    @GetMapping("/health")
    public String health() {
        return "PMS service is running";
    }

    @GetMapping("/valkey-test")
    public String testValkey() {

        redisTemplate.opsForValue()
                .set("pms:test", "Valkey connection successful");

        return redisTemplate.opsForValue()
                .get("pms:test");
    }
    @GetMapping("/redis-config")
    public String redisConfig(
            org.springframework.core.env.Environment environment) {

        String host = environment.getProperty("spring.data.redis.host");
        String port = environment.getProperty("spring.data.redis.port");
        String url = environment.getProperty("spring.data.redis.url");

        return "host=" + host +
                ", port=" + port +
                ", url=" + url;
    }
}
