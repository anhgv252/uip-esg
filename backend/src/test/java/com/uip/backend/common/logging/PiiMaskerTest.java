package com.uip.backend.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3.1-16: PII masking unit tests for email fields in log output.
 */
@DisplayName("PiiMasker — email masking")
class PiiMaskerTest {

    @ParameterizedTest
    @CsvSource({
            "john.doe@gmail.com,  j******@gmail.com",
            "a@b.co,              a******@b.co",
            "admin@uip.smartcity, a******@uip.smartcity",
            "x@test.org,          x******@test.org"
    })
    @DisplayName("maskEmail — standard emails masked correctly")
    void maskEmail_standardEmails(String input, String expected) {
        assertThat(PiiMasker.maskEmail(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("maskEmail — null returns 'null'")
    void maskEmail_null() {
        assertThat(PiiMasker.maskEmail(null)).isEqualTo("null");
    }

    @Test
    @DisplayName("maskEmail — empty string returned as-is")
    void maskEmail_emptyString() {
        assertThat(PiiMasker.maskEmail("")).isEmpty();
    }

    @Test
    @DisplayName("maskEmail — string without @ returned as-is")
    void maskEmail_noAtSign() {
        assertThat(PiiMasker.maskEmail("notanemail")).isEqualTo("notanemail");
    }

    @Test
    @DisplayName("maskEmail — @ at position 0 returned as-is")
    void maskEmail_atStartPosition() {
        assertThat(PiiMasker.maskEmail("@domain.com")).isEqualTo("@domain.com");
    }
}
