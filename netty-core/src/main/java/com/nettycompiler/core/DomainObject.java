package com.nettycompiler.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Abstract base for all domain objects that can be serialized to/from JSON.
 * Jackson resolves concrete types via a custom TypeIdResolver that
 * validates against a strict whitelist of registered types in DomainTypeRegistry.
 *
 * Every DomainObject subclass must be explicitly registered with
 * DomainTypeRegistry before it can be deserialized. Unregistered types
 * are rejected — no arbitrary class instantiation from JSON.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    include = JsonTypeInfo.As.PROPERTY,
    property = "_type"
)
public abstract class DomainObject {

    /**
     * The type discriminator string. Must match what is registered
     * in DomainTypeRegistry. Convention: lowercase_snake_case.
     */
    @JsonProperty("_type")
    public abstract String getDomainType();
}
