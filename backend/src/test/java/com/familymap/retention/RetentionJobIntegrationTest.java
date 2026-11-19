package com.familymap.retention;

import com.familymap.domain.Family;
import com.familymap.domain.Location;
import com.familymap.domain.SosAlert;
import com.familymap.domain.User;
import com.familymap.repository.FamilyRepository;
import com.familymap.repository.LocationRepository;
import com.familymap.repository.SosAlertRepository;
import com.familymap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Flyway schema on H2: proves the native batch queries and the cascades work. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "familymap.retention.batch-size=2") // force several batches
class RetentionJobIntegrationTest {

    @Autowired RetentionJob job;
    @Autowired LocationRepository locations;
    @Autowired SosAlertRepository sos;
    @Autowired UserRepository users;
    @Autowired FamilyRepository families;

    @Test
    void deletesOnlyWhatIsPastRetention() {
        User u = users.save(new User("Old", "old-" + UUID.randomUUID() + "@example.com", "hash"));
        Family f = families.save(new Family("Retention", "R" + UUID.randomUUID().toString().substring(0, 8), u.getId()));
        Instant now = Instant.now();

        List<Location> old = List.of(
                new Location(u.getId(), 1, 1, null, null, now.minus(Duration.ofDays(8))),
                new Location(u.getId(), 1, 1, null, null, now.minus(Duration.ofDays(9))),
                new Location(u.getId(), 1, 1, null, null, now.minus(Duration.ofDays(10))),
                new Location(u.getId(), 1, 1, null, null, now.minus(Duration.ofDays(11)).minusSeconds(1)),
                new Location(u.getId(), 1, 1, null, null, now.minus(Duration.ofDays(12))));
        locations.saveAll(old);
        Location fresh = locations.save(new Location(u.getId(), 2, 2, null, null, now.minus(Duration.ofDays(6))));

        SosAlert oldSos = sos.save(new SosAlert(u.getId(), Set.of(f.getId()), now.minus(Duration.ofDays(31))));
        SosAlert recentSos = sos.save(new SosAlert(u.getId(), Set.of(f.getId()), now.minus(Duration.ofDays(2))));

        RetentionJob.Result result = job.purge();

        assertThat(result.locations()).isGreaterThanOrEqualTo(5);
        assertThat(locations.findById(fresh.getId())).isPresent();
        old.forEach(l -> assertThat(locations.findById(l.getId())).isEmpty());
        assertThat(sos.findById(oldSos.getId())).isEmpty();
        assertThat(sos.findById(recentSos.getId())).isPresent();
    }
}
