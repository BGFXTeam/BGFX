package com.znfdev.zson.reflect;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a generic {@link Type} at compile time, the same trick Gson uses, e.g.:
 * <pre>Type listOfFoo = new TypeToken&lt;List&lt;Foo&gt;&gt;(){}.getType();</pre>
 */
public class TypeToken<T> {

    private final Type type;

    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType)) {
            throw new IllegalStateException(
                    "TypeToken must be created with a type parameter, e.g. new TypeToken<List<Foo>>(){}");
        }
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }

    private TypeToken(Type type) {
        this.type = type;
    }

    public static TypeToken<?> get(Type type) {
        return new TypeToken<Object>(type);
    }

    public static <T> TypeToken<T> get(Class<T> clazz) {
        return new TypeToken<T>((Type) clazz);
    }

    public final Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeToken && type.equals(((TypeToken<?>) o).type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
