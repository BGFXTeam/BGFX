package com.znfdev.zson;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import com.znfdev.zson.internal.ObjectBinder;
import com.znfdev.zson.internal.ObjectSerializer;

/**
 * A dependency-free, reflection-based JSON (de)serializer with the same call shape as
 * Google's Gson, written from scratch for Java 7 (no lambdas, no default methods).
 *
 * <pre>
 * Zson zson = new Zson();
 * String json = zson.toJson(myObject);
 * MyObject copy = zson.fromJson(json, MyObject.class);
 * </pre>
 *
 * For generic types, use {@link com.znfdev.zson.reflect.TypeToken}:
 * <pre>
 * Type listType = new TypeToken&lt;List&lt;MyObject&gt;&gt;(){}.getType();
 * List&lt;MyObject&gt; list = zson.fromJson(json, listType);
 * </pre>
 */
public final class Zson {

    private final boolean prettyPrinting;
    private final boolean serializeNulls;
    private final boolean requireExpose;

    public Zson() {
        this(false, false, false);
    }

    Zson(boolean prettyPrinting, boolean serializeNulls, boolean requireExpose) {
        this.prettyPrinting = prettyPrinting;
        this.serializeNulls = serializeNulls;
        this.requireExpose = requireExpose;
    }

    public String toJson(Object src) {
        return toJson(src, src == null ? Object.class : src.getClass());
    }

    public String toJson(Object src, Type typeOfSrc) {
        StringBuilder sb = new StringBuilder();
        new ObjectSerializer(serializeNulls, requireExpose, prettyPrinting).write(sb, src, typeOfSrc, 0);
        return sb.toString();
    }

    public <T> T fromJson(String json, Class<T> classOfT) {
        return fromJson(json, (Type) classOfT);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(String json, Type typeOfT) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.length() == 0) {
            return null;
        }
        Object tree = new com.znfdev.zson.internal.JsonParser(trimmed).parse();
        return (T) new ObjectBinder(requireExpose).bind(tree, typeOfT);
    }

    public <T> T fromJson(Reader reader, Class<T> classOfT) throws IOException {
        return fromJson(reader, (Type) classOfT);
    }

    public <T> T fromJson(Reader reader, Type typeOfT) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }
        return fromJson(sb.toString(), typeOfT);
    }
}
