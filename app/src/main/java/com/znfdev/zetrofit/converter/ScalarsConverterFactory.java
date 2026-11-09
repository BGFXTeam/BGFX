package com.znfdev.zetrofit.converter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.znfdev.zetrofit.Converter;
import com.znfdev.zetrofit.RequestBody;
import com.znfdev.zetrofit.ResponseBody;
import com.znfdev.zetrofit.Zetrofit;

/** Converts strings and boxed/primitive scalars to and from plain-text HTTP bodies. */
public final class ScalarsConverterFactory extends Converter.Factory {

    private ScalarsConverterFactory() {
    }

    public static ScalarsConverterFactory create() {
        return new ScalarsConverterFactory();
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Zetrofit zetrofit) {
        if (type == String.class) {
            return new Converter<ResponseBody, String>() {
                public String convert(ResponseBody value) throws IOException {
                    return value.string();
                }
            };
        }
        return null;
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations,
            Annotation[] methodAnnotations, Zetrofit zetrofit) {
        if (type == String.class) {
            return new Converter<String, RequestBody>() {
                public RequestBody convert(String value) {
                    return RequestBody.create("text/plain; charset=UTF-8", value);
                }
            };
        }
        return null;
    }
}
