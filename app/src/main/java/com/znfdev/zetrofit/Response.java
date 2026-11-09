package com.znfdev.zetrofit;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Wraps the raw HTTP result together with the converted (or error) body. */
public final class Response<T> {

    private final int code;
    private final String message;
    private final Map<String, List<String>> headers;
    private final T body;
    private final ResponseBody errorBody;

    private Response(int code, String message, Map<String, List<String>> headers, T body, ResponseBody errorBody) {
        this.code = code;
        this.message = message;
        this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
        this.body = body;
        this.errorBody = errorBody;
    }

    public static <T> Response<T> success(T body, int code, String message, Map<String, List<String>> headers) {
        return new Response<T>(code, message, headers, body, null);
    }

    public static <T> Response<T> error(ResponseBody errorBody, int code, String message, Map<String, List<String>> headers) {
        return new Response<T>(code, message, headers, null, errorBody);
    }

    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public T body() {
        return body;
    }

    public ResponseBody errorBody() {
        return errorBody;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }
}
