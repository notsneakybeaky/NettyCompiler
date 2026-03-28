package com.nettycompiler.ws.message;

import com.nettycompiler.core.DomainObject;
import com.nettycompiler.core.Message;

/**
 * Envelope for DomainObject payloads.
 * The "payload" field uses Jackson polymorphic deserialization
 * via the DomainObject._type discriminator and the whitelist resolver.
 *
 * Actions: "create", "update", "delete", "sync", "query"
 */
public class DomainObjectMessage extends Message {

    private String action;
    private DomainObject payload;

    @Override
    public String getType() {
        return "domain_object";
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public DomainObject getPayload() { return payload; }
    public void setPayload(DomainObject payload) { this.payload = payload; }
}
