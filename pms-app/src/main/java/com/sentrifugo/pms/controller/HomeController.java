package com.sentrifugo.pms.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class HomeController {
    private final StringRedisTemplate redisTemplate;

    public HomeController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/api/home/valkey-test")
    public String testValkey() {
        try {
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();

            return "Valkey connected successfully";
        } catch (Exception e) {
            return "Valkey connection failed: " + e.getMessage();
        }
    }
}
