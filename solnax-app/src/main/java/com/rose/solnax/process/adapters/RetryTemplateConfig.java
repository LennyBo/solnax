package com.rose.solnax.process.adapters;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.Map;

@Configuration
public class RetryTemplateConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry on 5xx codes only
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                3,                       // max attempts
                Map.of(IOException.class, true),
                true
        );

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(500);     // 0.5s
        backOff.setMultiplier(2.0);          // doubles: 0.5s → 1s → 2s
        backOff.setMaxInterval(5000);        // max 5s

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOff);

        return retryTemplate;
    }
}
