package com.knapp.kisoft.mock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class WebhookConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Executor replyCallbackExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "reply-callback");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public ScheduledExecutorService storageOrderScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "storage-order-status");
            t.setDaemon(true);
            return t;
        });
    }
}
