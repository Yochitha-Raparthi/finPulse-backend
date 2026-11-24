package com.finpulse.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RecoveryConfiguration {
    @Bean
    ThreadPoolTaskExecutor recoveryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(40);
        executor.setThreadNamePrefix("password-mail-");
        return executor;
    }
}
