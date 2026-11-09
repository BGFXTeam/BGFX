package com.znfdev.zetrofit;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/** Converts a value of type {@code F} to type {@code T}, e.g. JSON bytes to a POJO. */
public interface Converter<F, T> {

    T convert(F value) throws IOException;

    /**
     * Produces {@link Converter} instances for specific types. Implementations return
     * {@code null} from any method whose conversion they don't handle, letting Zetrofit
     * fall through to the next registered factory.
     */
    public abstract static class Factory {

        public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Zetrofit zetrofit) {
            return null;
        }

        public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations,
                Annotation[] methodAnnotations, Zetrofit zetrofit) {
            return null;
        }

        public Converter<?, String> stringConverter(Type type, Annotation[] annotations, Zetrofit zetrofit) {
            return null;
        }
    }
}
