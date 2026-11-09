package com.znfdev.zetrofit.converter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.znfdev.zson.Zson;

import com.znfdev.zetrofit.Converter;
import com.znfdev.zetrofit.RequestBody;
import com.znfdev.zetrofit.ResponseBody;
import com.znfdev.zetrofit.Zetrofit;

/**
 * Converts request/response bodies to and from JSON using Zson - a dependency-free,
 * from-scratch stand-in for GsonConverterFactory. Drop this file into
 * com.znfdev.zetrofit.converter alongside GsonConverterFactory to use Zetrofit + Zson
 * with zero third-party JSON dependency.
 */
public final class ZsonConverterFactory extends Converter.Factory {

    private final Zson zson;

    private ZsonConverterFactory(Zson zson) {
        this.zson = zson;
    }

    /** Creates an instance backed by a default {@code new Zson()}. */
    public static ZsonConverterFactory create() {
        return new ZsonConverterFactory(new Zson());
    }

    /** Creates an instance backed by a caller-supplied, pre-configured {@link Zson}. */
    public static ZsonConverterFactory create(Zson zson) {
        if (zson == null) {
            throw new IllegalArgumentException("zson == null");
        }
        return new ZsonConverterFactory(zson);
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(final Type type, Annotation[] annotations, Zetrofit zetrofit) {
        return new Converter<ResponseBody, Object>() {
            public Object convert(ResponseBody value) throws IOException {
                String json = value.string();
                if (json.length() == 0) {
                    return null;
                }
                return zson.fromJson(json, type);
            }
        };
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(final Type type, Annotation[] parameterAnnotations,
            Annotation[] methodAnnotations, Zetrofit zetrofit) {
        return new Converter<Object, RequestBody>() {
            public RequestBody convert(Object value) {
                String json = zson.toJson(value, type);
                return RequestBody.create("application/json; charset=UTF-8", json);
            }
        };
    }
}
