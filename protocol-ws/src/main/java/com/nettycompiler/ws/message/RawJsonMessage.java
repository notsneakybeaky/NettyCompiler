package com.nettycompiler.ws.message;

import com.nettycompiler.core.Message;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A fallback message type that captures any unregistered JSON
 * so it can be passed to the Python worker as-is.
 */
public class RawJsonMessage extends Message {
    private String type;
    private JsonNode data;

    public RawJsonMessage() {}

    public RawJsonMessage(String type, JsonNode data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public String getType() {
        return type;
    }

    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }
}