package com.znfdev.zson.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.znfdev.zson.annotations.Expose;
import com.znfdev.zson.annotations.SerializedName;

/** Walks an object graph via reflection and writes it out as JSON text. */
public final class ObjectSerializer {

    private final boolean serializeNulls;
    private final boolean requireExpose;
    private final boolean prettyPrinting;

    public ObjectSerializer(boolean serializeNulls, boolean requireExpose, boolean prettyPrinting) {
        this.serializeNulls = serializeNulls;
        this.requireExpose = requireExpose;
        this.prettyPrinting = prettyPrinting;
    }

    public void write(StringBuilder sb, Object value, Type declaredType, int indent) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof CharSequence || value instanceof Character) {
            writeQuoted(sb, value.toString());
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
            return;
        }
        Class<?> runtimeClass = value.getClass();
        if (runtimeClass.isEnum() || value instanceof Enum<?>) {
            writeQuoted(sb, enumJsonName((Enum<?>) value));
            return;
        }
        if (runtimeClass.isArray()) {
            writeArray(sb, value, indent);
            return;
        }
        if (value instanceof Collection<?>) {
            writeCollection(sb, (Collection<?>) value, Types.collectionElementType(declaredType), indent);
            return;
        }
        if (value instanceof Map<?, ?>) {
            writeMap(sb, (Map<?, ?>) value, Types.mapValueType(declaredType), indent);
            return;
        }
        writeObject(sb, value, indent);
    }

    private void writeArray(StringBuilder sb, Object array, int indent) {
        int length = Array.getLength(array);
        Class<?> componentType = array.getClass().getComponentType();
        sb.append('[');
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            newlineIndent(sb, indent + 1);
            write(sb, Array.get(array, i), componentType, indent + 1);
        }
        if (length > 0) {
            newlineIndent(sb, indent);
        }
        sb.append(']');
    }

    private void writeCollection(StringBuilder sb, Collection<?> collection, Type elementType, int indent) {
        sb.append('[');
        int i = 0;
        for (Object element : collection) {
            if (i++ > 0) {
                sb.append(',');
            }
            newlineIndent(sb, indent + 1);
            write(sb, element, elementType, indent + 1);
        }
        if (!collection.isEmpty()) {
            newlineIndent(sb, indent);
        }
        sb.append(']');
    }

    private void writeMap(StringBuilder sb, Map<?, ?> map, Type valueType, int indent) {
        sb.append('{');
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object v = entry.getValue();
            if (v == null && !serializeNulls) {
                continue;
            }
            if (i++ > 0) {
                sb.append(',');
            }
            newlineIndent(sb, indent + 1);
            writeQuoted(sb, String.valueOf(entry.getKey()));
            sb.append(prettyPrinting ? ": " : ":");
            write(sb, v, valueType, indent + 1);
        }
        if (i > 0) {
            newlineIndent(sb, indent);
        }
        sb.append('}');
    }

    private void writeObject(StringBuilder sb, Object value, int indent) {
        Map<String, Field> fields = collectFields(value.getClass());
        sb.append('{');
        int i = 0;
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            Field field = entry.getValue();
            if (requireExpose) {
                Expose expose = field.getAnnotation(Expose.class);
                if (expose == null || !expose.serialize()) {
                    continue;
                }
            }
            Object fieldValue;
            try {
                field.setAccessible(true);
                fieldValue = field.get(value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Unable to read field " + field, e);
            }
            if (fieldValue == null && !serializeNulls) {
                continue;
            }
            if (i++ > 0) {
                sb.append(',');
            }
            newlineIndent(sb, indent + 1);
            writeQuoted(sb, entry.getKey());
            sb.append(prettyPrinting ? ": " : ":");
            write(sb, fieldValue, field.getGenericType(), indent + 1);
        }
        if (i > 0) {
            newlineIndent(sb, indent);
        }
        sb.append('}');
    }

    /** Declared fields for the whole class hierarchy (excluding Object), keyed by JSON name. */
    static Map<String, Field> collectFields(Class<?> clazz) {
        Map<String, Field> result = new LinkedHashMap<String, Field>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] declared = current.getDeclaredFields();
            for (int i = 0; i < declared.length; i++) {
                Field field = declared[i];
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                String jsonName = jsonNameOf(field);
                if (!result.containsKey(jsonName)) {
                    result.put(jsonName, field);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    static String jsonNameOf(Field field) {
        SerializedName annotation = field.getAnnotation(SerializedName.class);
        return annotation != null ? annotation.value() : field.getName();
    }

    private static String enumJsonName(Enum<?> value) {
        try {
            Field field = value.getDeclaringClass().getField(value.name());
            SerializedName annotation = field.getAnnotation(SerializedName.class);
            if (annotation != null) {
                return annotation.value();
            }
        } catch (NoSuchFieldException ignored) {
            // fall through to default name
        }
        return value.name();
    }

    private void newlineIndent(StringBuilder sb, int indent) {
        if (!prettyPrinting) {
            return;
        }
        sb.append('\n');
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    private static void writeQuoted(StringBuilder sb, String s) {
        sb.append('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
