package com.nettycompiler.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.nettycompiler.core.DomainObject;
import com.nettycompiler.registry.DomainTypeRegistry;

/**
 * Custom Jackson TypeIdResolver that validates incoming "_type" values
 * against the DomainTypeRegistry whitelist. Any type not explicitly
 * registered is rejected — no arbitrary class instantiation from JSON.
 */
public class WhitelistTypeIdResolver extends TypeIdResolverBase {

    private final DomainTypeRegistry registry;

    public WhitelistTypeIdResolver(DomainTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String idFromValue(Object value) {
        if (value instanceof DomainObject domainObject) {
            return domainObject.getDomainType();
        }
        throw new IllegalArgumentException("Not a DomainObject: " + value.getClass().getName());
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        Class<? extends DomainObject> clazz = registry.resolve(id);
        if (clazz == null) {
            throw new IllegalArgumentException(
                "Unregistered DomainObject type: '" + id + "'. " +
                "All types must be whitelisted in DomainTypeRegistry. " +
                "Registered types: " + registry.getRegisteredTypes());
        }
        return context.constructType(clazz);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
