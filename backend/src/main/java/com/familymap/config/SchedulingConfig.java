package com.familymap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled}, used by the data retention job. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
