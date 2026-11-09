package com.znfdev.zetrofit;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/** A simple, in-memory HTTP request body: a content type plus raw bytes. */
public abstract class RequestBody {

    public abstract String contentType();

    public abstract byte[] content() throws IOException;

    public static RequestBody create(final String contentType, final String content) {
        if (content == null) {
            throw new IllegalArgumentException("content == null");
        }
        return new RequestBody() {
            public String contentType() {
                return contentType;
            }

            public byte[] content() throws IOException {
                try {
                    return content.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new AssertionError(e);
                }
            }
        };
    }

    public static RequestBody create(final String contentType, final byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content == null");
        }
        return new RequestBody() {
            public String contentType() {
                return contentType;
            }

            public byte[] content() {
                return content;
            }
        };
    }
}
