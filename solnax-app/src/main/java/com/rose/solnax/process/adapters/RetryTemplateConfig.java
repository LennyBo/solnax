package com.rose.solnax.process.adapters;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.Map;

@Configuration
@Slf4j
public class RetryTemplateConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry on 5xx codes only
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                2,                       // max attempts
                Map.of(IOException.class, true),
                true
        );

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(500);     // 0.5s
        backOff.setMultiplier(2.0);          // doubles: 0.5s → 1s → 2s
        backOff.setMaxInterval(5000);        // max 5s

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOff);

        retryTemplate.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(
                    RetryContext context,
                    RetryCallback<T, E> callback) {
                return true;
            }

            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context,
                    RetryCallback<T, E> callback,
                    Throwable throwable) {

                log.warn(
                        "Retry attempt {} failed due to: {}",
                        context.getRetryCount(),
                        throwable.getMessage()
                );
            }

            @Override
            public <T, E extends Throwable> void close(
                    RetryContext context,
                    RetryCallback<T, E> callback,
                    Throwable throwable) {

                if (throwable == null) {
                    log.info("Retry succeeded after {} attempt(s)",
                            context.getRetryCount());
                } else {
                    log.error("Retry exhausted after {} attempts",
                            context.getRetryCount(), throwable);
                }
            }
        });

        return retryTemplate;
    }
}
