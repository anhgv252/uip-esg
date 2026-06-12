package com.uip.backend.common.logging;

/**
 * PII masking utility for log output.
 * Applies masking patterns for email and other personally identifiable information.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "john.doe@gmail.com"} → {@code "j*******@gmail.com"}</li>
 *   <li>{@code "a@b.co"} → {@code "a***@b.co"}</li>
 *   <li>{@code null} → {@code "null"}</li>
 * </ul>
 */
public final class PiiMasker {

    private PiiMasker() {}

    /**
     * Mask an email address: keep first character of local part, replace rest with asterisks.
     * Non-email strings are returned as-is (for use in structured log parameters).
     *
     * @param email the email to mask, may be null
     * @return masked email, e.g. "j***@gmail.com", or "null" if input is null
     */
    public static String maskEmail(String email) {
        if (email == null) return "null";
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;  // not a valid email format, return as-is
        return email.charAt(0) + "******" + email.substring(atIndex);
    }
}
