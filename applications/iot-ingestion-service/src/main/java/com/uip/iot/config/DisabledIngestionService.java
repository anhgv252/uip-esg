package com.uip.iot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "iot.ingestion.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledIngestionService implements IngestionService {

    @Override
    public void ingest(String topic, String key, String value) {
        // no-op
    }

    @Override
    public String getMode() {
        return "DISABLED";
    }
}
