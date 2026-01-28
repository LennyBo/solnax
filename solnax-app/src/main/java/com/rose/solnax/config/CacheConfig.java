package com.rose.solnax.config;


import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        CaffeineCache powerLogsCache = new CaffeineCache(
                "power_logs",
                Caffeine.newBuilder()
                        .initialCapacity(10)
                        .maximumSize(500)
                        .expireAfterWrite(1, TimeUnit.SECONDS)
                        .recordStats()
                        .build()
        );

        CaffeineCache usersCache = new CaffeineCache(
                "tesla-ble",
                Caffeine.newBuilder()
                        .initialCapacity(10)
                        .maximumSize(500)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build()
        );

        cacheManager.setCaches(List.of(powerLogsCache, usersCache));
        return cacheManager;
    }


}
