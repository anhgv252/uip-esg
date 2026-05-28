package com.uip.iot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "iot.ingestion.mode", havingValue = "shadow")
public class ShadowIngestionService implements IngestionService {

    @Override
    public void ingest(String topic, String key, String value) {
        log.info("[SHADOW] Ingesting: topic={} key={}", topic, key);
    }

    @Override
    public String getMode() {
        return "SHADOW";
    }
}
