package com.familymap.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates invite codes like {@code K7QM-3XPA}: 8 random characters from a 32-symbol
 * alphabet with no look-alikes (no 0/O, 1/I), so codes are easy to read aloud or type.
 *
 * <p>32^8 ≈ 1.1 trillion combinations, drawn from {@link SecureRandom}, so codes can't be
 * guessed or enumerated in practice.
 */
@Component
public class InviteCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    /** Returns a new code in display form, e.g. {@code K7QM-3XPA}. */
    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Puts user input into the stored form so "k7qm 3xpa", "K7QM3XPA" and "k7qm-3xpa" all
     * match {@code K7QM-3XPA}.
     */
    public static String normalize(String input) {
        String compact = input.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
        if (compact.length() != LENGTH) {
            return compact; // won't match anything; the lookup simply returns "not found"
        }
        return compact.substring(0, LENGTH / 2) + "-" + compact.substring(LENGTH / 2);
    }
}
