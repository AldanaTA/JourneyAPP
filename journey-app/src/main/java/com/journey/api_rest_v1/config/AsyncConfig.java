package com.journey.api_rest_v1.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async}, used to send emails without making the request wait (or reveal timing). */
@Configuration
@EnableAsync
@EnableConfigurationProperties(MailProperties.class)
public class AsyncConfig {
}
