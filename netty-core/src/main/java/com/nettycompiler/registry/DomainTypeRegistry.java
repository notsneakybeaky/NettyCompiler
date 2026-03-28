package com.nettycompiler.registry;

import com.nettycompiler.core.DomainObject;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strict whitelist of DomainObject subtypes allowed for Jackson deserialization.
 * Thread-safe. New types are registered at startup or via runtime schema loading.
 * Jackson's WhitelistTypeIdResolver consults this registry — any type string
 * not present here causes deserialization to fail with a clear error.
 */
public class DomainTypeRegistry {

    private final Map<String, Class<? extends DomainObject>> whitelist = new ConcurrentHashMap<>();

    /**
     * Register a domain type. Must be called before any JSON containing this
     * type discriminator is deserialized.
     *
     * @param typeName the "_type" discriminator value (e.g. "retail_order")
     * @param clazz    the concrete DomainObject subclass
     */
    public void register(String typeName, Class<? extends DomainObject> clazz) {
        whitelist.put(typeName, clazz);
    }

    /**
     * Look up a registered type by its discriminator string.
     * Returns null if not registered (Jackson layer should reject).
     */
    public Class<? extends DomainObject> resolve(String typeName) {
        return whitelist.get(typeName);
    }

    public boolean isRegistered(String typeName) {
        return whitelist.containsKey(typeName);
    }

    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(whitelist.keySet());
    }

    public int size() {
        return whitelist.size();
    }
}
