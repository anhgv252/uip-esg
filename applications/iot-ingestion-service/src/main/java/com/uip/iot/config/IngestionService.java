package com.uip.iot.config;

public interface IngestionService {
    void ingest(String topic, String key, String value);
    String getMode();
}
