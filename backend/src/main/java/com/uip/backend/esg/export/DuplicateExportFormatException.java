package com.uip.backend.esg.export;

public class DuplicateExportFormatException extends RuntimeException {
    public DuplicateExportFormatException(String formatId, String bean1, String bean2) {
        super("Duplicate export format '%s': found in both '%s' and '%s'".formatted(formatId, bean1, bean2));
    }
}
