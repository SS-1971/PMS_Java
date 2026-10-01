package com.sentrifugo.pms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;

// The library modules (pms-security, pms-db, ...) live under com.sentrifugo.*,
// a sibling of this class's package, so they must be scanned explicitly.
@SpringBootApplication(scanBasePackages = "com.sentrifugo")
public class PmsApplication implements CommandLineRunner {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public static void main(String[] args) {
        SpringApplication.run(PmsApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            redisTemplate.opsForValue().set("testKey", "testValue");
            String value = redisTemplate.opsForValue().get("testKey");
            System.out.println("Redis connection successful. Retrieved value: " + value);
        } catch (Exception e) {
            System.err.println("Redis connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}