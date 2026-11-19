package com.familymap.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long data is kept, bound from {@code familymap.retention.*}.
 *
 * @param locations location points (the API rejects points older than 7 days, so this should
 *                  not be shorter)
 * @param sos       SOS alerts, open or resolved
 * @param batchSize rows deleted per transaction, so a big purge never locks the table for long
 * @param cron      when the purge runs (Spring cron); "-" disables it
 */
@ConfigurationProperties(prefix = "familymap.retention")
public record RetentionProperties(Duration locations, Duration sos, Integer batchSize, String cron) {

    public RetentionProperties {
        if (locations == null) {
            locations = Duration.ofDays(7);
        }
        if (sos == null) {
            sos = Duration.ofDays(30);
        }
        if (batchSize == null || batchSize <= 0) {
            batchSize = 1_000;
        }
    }
}
