package com.familymap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the FamilyMap API.
 *
 * <p>FamilyMap is a consent-based family location-sharing service: every member
 * explicitly opts in, always sees that sharing is active, and can pause it.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FamilyMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyMapApplication.class, args);
    }
}
