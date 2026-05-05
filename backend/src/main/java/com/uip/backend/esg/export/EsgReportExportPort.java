package com.uip.backend.esg.export;

public interface EsgReportExportPort {

    String getFormatId();

    String getContentType();

    String getFileExtension();

    byte[] export(EsgReportData data);
}
