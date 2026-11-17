package com.acme.sportplatform.common;

public final class DemoDataException extends RuntimeException {
    private final String code;
    private final int status;
    public DemoDataException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public String code() { return code; }
    public int status() { return status; }
}
