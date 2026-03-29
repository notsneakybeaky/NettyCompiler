package com.nettycompiler.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * Builds and configures the singleton ObjectMapper.
 * snake_case property naming for all JSON contracts.
 * No domain-specific type resolution — the server does NOT
 * deserialize or validate domain-specific payloads.
 */
public class JacksonFactory {

    private final ObjectMapper mapper;

    public JacksonFactory() {
        this.mapper = buildMapper();
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    private ObjectMapper buildMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }
}