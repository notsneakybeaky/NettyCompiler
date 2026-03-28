package com.nettycompiler.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A DomainObject whose structure is defined at runtime by a schema.
 * Fields are stored as a map. Jackson serializes/deserializes via @JsonAnyGetter/@JsonAnySetter.
 * Used when no compile-time Java class exists for a domain type — the schema
 * drives validation instead.
 */
public class DynamicDomainObject extends DomainObject {

    private String domainType;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public DynamicDomainObject() {}

    public DynamicDomainObject(String domainType) {
        this.domainType = domainType;
    }

    @Override
    public String getDomainType() {
        return domainType;
    }

    public void setDomainType(String domainType) {
        this.domainType = domainType;
    }

    @JsonAnyGetter
    public Map<String, Object> getFields() {
        return fields;
    }

    @JsonAnySetter
    public void setField(String name, Object value) {
        fields.put(name, value);
    }

    public Object getField(String name) {
        return fields.get(name);
    }
}
