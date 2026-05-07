package com.catrescue.api.service.client;

public final class OpenAiInvocationException extends RuntimeException {

    public OpenAiInvocationException(String message) {
        super(message);
    }

    public OpenAiInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
