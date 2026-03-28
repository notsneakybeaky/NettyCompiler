package com.nettycompiler.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.nettycompiler.core.DomainObject;
import com.nettycompiler.registry.DomainTypeRegistry;

/**
 * Builds and configures the singleton ObjectMapper.
 * - snake_case property naming for JSON contracts
 * - DomainObject type resolution via WhitelistTypeIdResolver
 * - Strict: rejects unknown domain types
 */
public class JacksonFactory {

    private final ObjectMapper mapper;
    private final DomainTypeRegistry registry;

    public JacksonFactory(DomainTypeRegistry registry) {
        this.registry = registry;
        this.mapper = buildMapper();
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public DomainTypeRegistry getRegistry() {
        return registry;
    }

    private ObjectMapper buildMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // Register the whitelist-based type resolver for DomainObject hierarchy.
        // This makes Jackson use our WhitelistTypeIdResolver for any field typed
        // as DomainObject, validating the "_type" discriminator against the registry.
        WhitelistTypeIdResolver resolver = new WhitelistTypeIdResolver(registry);

        TypeResolverBuilder<?> typer = new StdTypeResolverBuilder()
                .init(JsonTypeInfo.Id.CUSTOM, resolver)
                .inclusion(JsonTypeInfo.As.PROPERTY)
                .typeProperty("_type");

        // Apply only to DomainObject subtypes, not all classes
        om.setDefaultTyping(new DomainObjectTypeResolverBuilder(typer));

        return om;
    }

    /**
     * Custom DefaultTypeResolverBuilder that only activates for DomainObject subtypes.
     * This prevents the type resolver from interfering with normal POJO serialization
     * (Message, String, Map, etc.) — only DomainObject fields get the "_type" treatment.
     */
    private static class DomainObjectTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {

        private final TypeResolverBuilder<?> delegate;

        DomainObjectTypeResolverBuilder(TypeResolverBuilder<?> delegate) {
            super(ObjectMapper.DefaultTyping.NON_FINAL);
            this.delegate = delegate;
        }

        @Override
        public boolean useForType(com.fasterxml.jackson.databind.JavaType t) {
            return DomainObject.class.isAssignableFrom(t.getRawClass());
        }
    }
}
