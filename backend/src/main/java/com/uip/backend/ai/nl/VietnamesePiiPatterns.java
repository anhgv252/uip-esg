package com.uip.backend.ai.nl;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * PII pattern detection for Vietnamese text.
 * 
 * <p>ADR-049 §5.3: Phase 1 regex implementation.
 * <p>Phase 2 (M5-4): integrate underthesea NER if false-positive rate > 5%.
 * 
 * <p>Patterns detect:
 * <ul>
 *   <li>Vietnamese full names (surname + middle + given name)</li>
 *   <li>CCCD (Citizen ID) — 12 digits</li>
 *   <li>Vietnamese phone numbers (10 digits, 03x/05x/07x/08x/09x)</li>
 *   <li>Passport (B + 7 digits)</li>
 * </ul>
 */
@Component
public class VietnamesePiiPatterns {

    // Full Vietnamese name: common surname + at least 2 more words
    // Conservative: requires surname + middle + given name minimum
    private static final Pattern FULL_NAME = Pattern.compile(
        "\\b(Nguyễn|Trần|Lê|Phạm|Huỳnh|Hoàng|Phan|Vũ|Võ|Đặng|Bùi|Đỗ|Hồ|Ngô|Dương|Lý)" +
        "\\s+[A-ZÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬĐÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴ]" +
        "[a-zàáảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]+" +
        "(\\s+[A-ZÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬĐÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴ]" +
        "[a-zàáảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]+)+\\b"
    );

    // CCCD: 12 digits, optionally spaced in groups of 3
    private static final Pattern CCCD = Pattern.compile(
        "\\b\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{3}\\b"
    );

    // Vietnamese phone: 10 digits starting 03x/05x/07x/08x/09x or +84
    private static final Pattern PHONE = Pattern.compile(
        "\\b(\\+84|0)(3[2-9]|5[6-9]|7[0-9]|8[0-9]|9[0-9])[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b"
    );

    // Passport: B + 7 digits (common VN format)
    private static final Pattern PASSPORT = Pattern.compile("\\bB\\d{7}\\b");

    /**
     * Check if text contains any PII patterns.
     * 
     * @param text Vietnamese text to scan
     * @return true if any PII pattern detected
     */
    public boolean hasPii(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        
        return FULL_NAME.matcher(text).find()
            || CCCD.matcher(text).find()
            || PHONE.matcher(text).find()
            || PASSPORT.matcher(text).find();
    }
}
