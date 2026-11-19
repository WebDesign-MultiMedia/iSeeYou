package com.familymap.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @RepeatedTest(20)
    void codesUseDisplayFormatAndUnambiguousAlphabet() {
        String code = generator.generate();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
        assertThat(code).doesNotContain("0", "O", "1", "I");
    }

    @Test
    void codesDoNotRepeatInPractice() {
        Set<String> seen = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> seen.add(generator.generate()));
        assertThat(seen).hasSize(10_000);
    }

    @Test
    void normalizeAcceptsCommonTypingVariants() {
        assertThat(InviteCodeGenerator.normalize("k7qm-3xpa")).isEqualTo("K7QM-3XPA");
        assertThat(InviteCodeGenerator.normalize(" K7QM 3XPA ")).isEqualTo("K7QM-3XPA");
        assertThat(InviteCodeGenerator.normalize("K7QM3XPA")).isEqualTo("K7QM-3XPA");
    }

    @Test
    void normalizeLeavesWrongLengthInputUnmatched() {
        assertThat(InviteCodeGenerator.normalize("abc")).isEqualTo("ABC");
    }
}
