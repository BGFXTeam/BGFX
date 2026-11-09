package com.znfdev.zetrofit;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

final class Utils {

    private Utils() {
    }

    static <T> T checkNotNull(T value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        return value;
    }

    /** Returns the {@code T} in {@code Call<T>}, or Object if it can't be determined. */
    static Type getCallResponseType(Type returnType) {
        if (returnType instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) returnType;
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length == 1) {
                return args[0];
            }
        }
        return Object.class;
    }

    static Class<?> rawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Object raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class<?>) {
                return (Class<?>) raw;
            }
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    static boolean isPrimitiveOrWrapperOrString(Class<?> c) {
        return c == String.class
                || c == boolean.class || c == Boolean.class
                || c == byte.class || c == Byte.class
                || c == short.class || c == Short.class
                || c == int.class || c == Integer.class
                || c == long.class || c == Long.class
                || c == float.class || c == Float.class
                || c == double.class || c == Double.class
                || c == char.class || c == Character.class;
    }
}
