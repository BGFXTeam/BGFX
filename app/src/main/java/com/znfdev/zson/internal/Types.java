package com.znfdev.zson.internal;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Small helpers for pulling raw classes and generic type arguments out of a {@link Type}. */
public final class Types {

    private Types() {
    }

    public static Class<?> rawType(Type type) {
        if (type == null) {
            return Object.class;
        }
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Object raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class<?>) {
                return (Class<?>) raw;
            }
        }
        if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        return Object.class;
    }

    /** The {@code E} in {@code List<E>} / {@code Set<E>}, or Object.class if unknown. */
    public static Type collectionElementType(Type type) {
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (args.length >= 1) {
                return args[0];
            }
        }
        return Object.class;
    }

    /** The {@code V} in {@code Map<K, V>}, or Object.class if unknown. */
    public static Type mapValueType(Type type) {
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (args.length >= 2) {
                return args[1];
            }
        }
        return Object.class;
    }
}
