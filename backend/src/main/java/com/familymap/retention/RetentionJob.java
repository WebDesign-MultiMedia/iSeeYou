package com.familymap.retention;

import com.familymap.repository.LocationRepository;
import com.familymap.repository.SosAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deletes location points older than 7 days and SOS alerts older than 30 days (configurable).
 * A location trail is the most sensitive thing this app stores; keeping it only as long as
 * it's useful is the point.
 *
 * <p>Works in small batches, each in its own transaction, so a large backlog never holds a long
 * lock on the busiest table. With more than one API instance, add a lock (e.g. ShedLock) so only
 * one runs it; running twice is harmless, just wasted work.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final LocationRepository locations;
    private final SosAlertRepository sos;
    private final RetentionProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RetentionJob(LocationRepository locations, SosAlertRepository sos, RetentionProperties properties,
                        TransactionTemplate tx, Clock clock) {
        this.locations = locations;
        this.sos = sos;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    public record Result(long locations, long sosAlerts) {
    }

    @Scheduled(cron = "${familymap.retention.cron:0 15 * * * *}")
    public Result purge() {
        Instant now = clock.instant();
        long deletedLocations = purgeLocations(now.minus(properties.locations()));
        long deletedSos = purgeSos(now.minus(properties.sos()));
        if (deletedLocations + deletedSos > 0) {
            log.info("Retention: deleted {} location points and {} SOS alerts", deletedLocations, deletedSos);
        }
        return new Result(deletedLocations, deletedSos);
    }

    private long purgeLocations(Instant cutoff) {
        long total = 0;
        while (true) {
            Integer n = tx.execute(s -> {
                List<Long> ids = locations.findIdsRecordedBefore(cutoff, properties.batchSize());
                locations.deleteAllByIdInBatch(ids);
                return ids.size();
            });
            total += n == null ? 0 : n;
            if (n == null || n < properties.batchSize()) {
                return total;
            }
        }
    }

    private long purgeSos(Instant cutoff) {
        long total = 0;
        while (true) {
            Integer n = tx.execute(s -> {
                List<UUID> ids = sos.findIdsCreatedBefore(cutoff, properties.batchSize()).stream()
                        .map(RetentionJob::uuid).toList();
                sos.deleteAllByIdInBatch(ids); // circle links go with them (ON DELETE CASCADE)
                return ids.size();
            });
            total += n == null ? 0 : n;
            if (n == null || n < properties.batchSize()) {
                return total;
            }
        }
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        return new UUID(b.getLong(), b.getLong());
    }
}
