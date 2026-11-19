package com.familymap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async}, used to send push notifications off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
