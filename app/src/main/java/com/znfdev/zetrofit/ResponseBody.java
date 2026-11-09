package com.znfdev.zetrofit;

import java.io.IOException;

/** A simple, in-memory HTTP response body: a content type plus raw bytes. */
public final class ResponseBody {

    private final String contentType;
    private final byte[] bytes;

    private ResponseBody(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    public static ResponseBody create(String contentType, byte[] bytes) {
        return new ResponseBody(contentType, bytes);
    }

    public String contentType() {
        return contentType;
    }

    public byte[] bytes() {
        return bytes;
    }

    public String string() throws IOException {
        return new String(bytes, "UTF-8");
    }
}
