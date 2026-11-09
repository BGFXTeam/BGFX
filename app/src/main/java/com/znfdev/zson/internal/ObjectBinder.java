package com.znfdev.zson.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.znfdev.zson.JsonSyntaxException;
import com.znfdev.zson.annotations.Expose;
import com.znfdev.zson.annotations.SerializedName;

/** Binds a parsed JSON tree (Map/List/String/Long/Double/Boolean/null) onto a target {@link Type}. */
public final class ObjectBinder {

    private final boolean requireExpose;

    public ObjectBinder() {
        this(false);
    }

    public ObjectBinder(boolean requireExpose) {
        this.requireExpose = requireExpose;
    }

    @SuppressWarnings("unchecked")
    public Object bind(Object tree, Type targetType) {
        Class<?> rawType = Types.rawType(targetType);

        if (rawType == Object.class) {
            return tree;
        }
        if (tree == null) {
            return null;
        }
        if (rawType == String.class) {
            return tree instanceof String ? tree : String.valueOf(tree);
        }
        if (isNumberOrBoolean(rawType)) {
            return coerceScalar(tree, rawType);
        }
        if (rawType.isEnum()) {
            return bindEnum(tree, (Class<Enum>) rawType);
        }
        if (rawType.isArray()) {
            return bindArray(tree, rawType, targetType);
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            return bindCollection(tree, rawType, targetType);
        }
        if (Map.class.isAssignableFrom(rawType)) {
            return bindMap(tree, rawType, targetType);
        }
        return bindObject(tree, rawType);
    }

    private boolean isNumberOrBoolean(Class<?> c) {
        return c == boolean.class || c == Boolean.class
                || c == byte.class || c == Byte.class
                || c == short.class || c == Short.class
                || c == int.class || c == Integer.class
                || c == long.class || c == Long.class
                || c == float.class || c == Float.class
                || c == double.class || c == Double.class
                || c == char.class || c == Character.class;
    }

    private Object coerceScalar(Object tree, Class<?> target) {
        if (target == boolean.class || target == Boolean.class) {
            if (tree instanceof Boolean) {
                return tree;
            }
            return Boolean.valueOf(String.valueOf(tree));
        }
        if (target == char.class || target == Character.class) {
            String s = String.valueOf(tree);
            return s.length() > 0 ? Character.valueOf(s.charAt(0)) : Character.valueOf('\0');
        }
        double d = tree instanceof Number ? ((Number) tree).doubleValue() : Double.parseDouble(String.valueOf(tree));
        if (target == byte.class || target == Byte.class) {
            return Byte.valueOf((byte) d);
        }
        if (target == short.class || target == Short.class) {
            return Short.valueOf((short) d);
        }
        if (target == int.class || target == Integer.class) {
            return Integer.valueOf((int) d);
        }
        if (target == long.class || target == Long.class) {
            return Long.valueOf((long) d);
        }
        if (target == float.class || target == Float.class) {
            return Float.valueOf((float) d);
        }
        return Double.valueOf(d);
    }

    static Object defaultForPrimitive(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.FALSE;
        if (primitive == char.class) return Character.valueOf('\0');
        if (primitive == byte.class) return Byte.valueOf((byte) 0);
        if (primitive == short.class) return Short.valueOf((short) 0);
        if (primitive == int.class) return Integer.valueOf(0);
        if (primitive == long.class) return Long.valueOf(0L);
        if (primitive == float.class) return Float.valueOf(0f);
        if (primitive == double.class) return Double.valueOf(0d);
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object bindEnum(Object tree, Class<Enum> enumClass) {
        String name = String.valueOf(tree);
        Object[] constants = enumClass.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            Enum<?> constant = (Enum<?>) constants[i];
            if (constant.name().equals(name)) {
                return constant;
            }
            try {
                Field field = enumClass.getField(constant.name());
                SerializedName annotation = field.getAnnotation(SerializedName.class);
                if (annotation != null && annotation.value().equals(name)) {
                    return constant;
                }
            } catch (NoSuchFieldException ignored) {
                // no annotation on this constant, nothing to match
            }
        }
        throw new JsonSyntaxException("No enum constant " + enumClass.getName() + "." + name);
    }

    private Object bindArray(Object tree, Class<?> rawType, Type targetType) {
        if (!(tree instanceof List)) {
            throw new JsonSyntaxException("Expected a JSON array for " + rawType.getName());
        }
        List<?> list = (List<?>) tree;
        Class<?> componentType = rawType.getComponentType();
        Object array = Array.newInstance(componentType, list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, bind(list.get(i), componentType));
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    private Object bindCollection(Object tree, Class<?> rawType, Type targetType) {
        if (!(tree instanceof List)) {
            throw new JsonSyntaxException("Expected a JSON array for " + rawType.getName());
        }
        List<?> list = (List<?>) tree;
        Collection<Object> collection = newCollectionInstance(rawType);
        Type elementType = Types.collectionElementType(targetType);
        for (int i = 0; i < list.size(); i++) {
            collection.add(bind(list.get(i), elementType));
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> newCollectionInstance(Class<?> rawType) {
        if (rawType.isInterface()) {
            if (Set.class.isAssignableFrom(rawType)) {
                return new LinkedHashSet<Object>();
            }
            return new ArrayList<Object>();
        }
        try {
            return (Collection<Object>) rawType.newInstance();
        } catch (Exception e) {
            return rawType == HashSet.class ? new HashSet<Object>() : new ArrayList<Object>();
        }
    }

    @SuppressWarnings("unchecked")
    private Object bindMap(Object tree, Class<?> rawType, Type targetType) {
        if (!(tree instanceof Map)) {
            throw new JsonSyntaxException("Expected a JSON object for " + rawType.getName());
        }
        Map<String, Object> source = (Map<String, Object>) tree;
        Map<String, Object> result;
        if (rawType.isInterface()) {
            result = new LinkedHashMap<String, Object>();
        } else {
            try {
                result = (Map<String, Object>) rawType.newInstance();
            } catch (Exception e) {
                result = new LinkedHashMap<String, Object>();
            }
        }
        Type valueType = Types.mapValueType(targetType);
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), bind(entry.getValue(), valueType));
        }
        return result;
    }

    private Object bindObject(Object tree, Class<?> rawType) {
        if (!(tree instanceof Map)) {
            throw new JsonSyntaxException("Expected a JSON object for " + rawType.getName());
        }
        Map<?, ?> source = (Map<?, ?>) tree;
        Object instance = instantiate(rawType);

        Map<String, Field> fields = ObjectSerializer.collectFields(rawType);
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            Field field = entry.getValue();
            if (requireExpose) {
                Expose expose = field.getAnnotation(Expose.class);
                if (expose == null || !expose.deserialize()) {
                    continue;
                }
            }
            Object rawValue = findValue(source, entry.getKey(), field);
            if (rawValue == NOT_FOUND) {
                continue;
            }
            Object boundValue = bind(rawValue, field.getGenericType());
            if (boundValue == null && field.getType().isPrimitive()) {
                boundValue = defaultForPrimitive(field.getType());
            }
            try {
                field.setAccessible(true);
                field.set(instance, boundValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Unable to set field " + field, e);
            }
        }
        return instance;
    }

    private static final Object NOT_FOUND = new Object();

    private Object findValue(Map<?, ?> source, String primaryName, Field field) {
        if (source.containsKey(primaryName)) {
            return source.get(primaryName);
        }
        SerializedName annotation = field.getAnnotation(SerializedName.class);
        if (annotation != null) {
            String[] alternates = annotation.alternate();
            for (int i = 0; i < alternates.length; i++) {
                if (source.containsKey(alternates[i])) {
                    return source.get(alternates[i]);
                }
            }
        }
        return NOT_FOUND;
    }

    private Object instantiate(Class<?> rawType) {
        try {
            Constructor<?> constructor = rawType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new JsonSyntaxException("Unable to create an instance of " + rawType.getName()
                    + " - it needs a no-argument constructor (Zson does not bypass constructors like Gson can).", e);
        }
    }
}
