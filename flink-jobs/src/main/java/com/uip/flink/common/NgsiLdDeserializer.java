package com.uip.flink.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

public class NgsiLdDeserializer implements DeserializationSchema<NgsiLdMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public NgsiLdMessage deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }
        return MAPPER.readValue(message, NgsiLdMessage.class);
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
