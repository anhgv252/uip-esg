package com.uip.flink.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NgsiLdDeserializer implements DeserializationSchema<NgsiLdMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(NgsiLdDeserializer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public NgsiLdMessage deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }
        try {
            // Fast path: message is a JSON object { ... }
            if (message[0] == '{') {
                return MAPPER.readValue(message, NgsiLdMessage.class);
            }
            // Backend may publish the payload as a JSON string "\"{ ... }\"" (double-encoded).
            // Unwrap the outer string layer before deserializing.
            if (message[0] == '"') {
                String unwrapped = MAPPER.readValue(message, String.class);
                return MAPPER.readValue(unwrapped, NgsiLdMessage.class);
            }
            return MAPPER.readValue(message, NgsiLdMessage.class);
        } catch (Exception e) {
            // Skip malformed messages (e.g. perf-test random bytes) — log and return null
            // The downstream filter(msg -> msg != null && msg.getDeviceIdValue() != null) will drop these.
            LOG.warn("Skipping malformed message (len={}): {}", message.length, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(NgsiLdMessage nextElement) {
        return false;
    }

    @Override
    public TypeInformation<NgsiLdMessage> getProducedType() {
        return TypeInformation.of(NgsiLdMessage.class);
    }
}
