package com.catrescue.api.service.client;

public final class GeminiInvocationException extends RuntimeException {

    public GeminiInvocationException(String message) {
        super(message);
    }

    public GeminiInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
