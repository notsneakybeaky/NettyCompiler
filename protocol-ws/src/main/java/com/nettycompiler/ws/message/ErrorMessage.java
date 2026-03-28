package com.nettycompiler.ws.message;

import com.nettycompiler.core.Message;

/**
 * Server-sent error envelope.
 */
public class ErrorMessage extends Message {

    private String code;
    private String detail;

    public ErrorMessage() {}

    public ErrorMessage(String code, String detail) {
        this.code = code;
        this.detail = detail;
    }

    @Override
    public String getType() {
        return "error";
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
