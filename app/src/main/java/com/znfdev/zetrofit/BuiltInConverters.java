package com.znfdev.zetrofit;

import java.io.IOException;
import java.lang.reflect.Type;

/** Fallback converters used when no registered {@link Converter.Factory} claims a type. */
final class BuiltInConverters {

    private BuiltInConverters() {
    }

    static Converter<ResponseBody, ?> toResponseBodyConverter(Type type) {
        if (type == ResponseBody.class) {
            return new Converter<ResponseBody, ResponseBody>() {
                public ResponseBody convert(ResponseBody value) {
                    return value;
                }
            };
        }
        if (type == String.class) {
            return new Converter<ResponseBody, String>() {
                public String convert(ResponseBody value) throws IOException {
                    return value.string();
                }
            };
        }
        if (type == Void.class || type == void.class) {
            return new Converter<ResponseBody, Void>() {
                public Void convert(ResponseBody value) {
                    return null;
                }
            };
        }
        return new Converter<ResponseBody, ResponseBody>() {
            public ResponseBody convert(ResponseBody value) {
                return value;
            }
        };
    }

    static Converter<?, RequestBody> toRequestBodyConverter(Type type) {
        if (type == RequestBody.class) {
            return new Converter<RequestBody, RequestBody>() {
                public RequestBody convert(RequestBody value) {
                    return value;
                }
            };
        }
        return new Converter<Object, RequestBody>() {
            public RequestBody convert(Object value) {
                return RequestBody.create("text/plain; charset=UTF-8", String.valueOf(value));
            }
        };
    }
}
