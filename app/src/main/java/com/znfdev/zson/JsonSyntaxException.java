package com.znfdev.zson;

/** Thrown when JSON text is malformed or doesn't match the requested target type. */
public class JsonSyntaxException extends RuntimeException {

    public JsonSyntaxException(String message) {
        super(message);
    }

    public JsonSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
