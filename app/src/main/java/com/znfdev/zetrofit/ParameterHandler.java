package com.znfdev.zetrofit;

import java.io.IOException;
import java.util.Map;

/** Applies a single method parameter's value onto a {@link RequestBuilder}. */
abstract class ParameterHandler<T> {

    abstract void apply(RequestBuilder builder, T value) throws IOException;

    static final class Path<T> extends ParameterHandler<T> {
        private final String name;
        private final boolean encoded;

        Path(String name, boolean encoded) {
            this.name = name;
            this.encoded = encoded;
        }

        void apply(RequestBuilder builder, T value) {
            if (value == null) {
                throw new IllegalArgumentException("Path parameter \"" + name + "\" value must not be null.");
            }
            builder.addPathParam(name, String.valueOf(value), encoded);
        }
    }

    static final class Query<T> extends ParameterHandler<T> {
        private final String name;
        private final boolean encoded;

        Query(String name, boolean encoded) {
            this.name = name;
            this.encoded = encoded;
        }

        void apply(RequestBuilder builder, T value) {
            if (value != null) {
                builder.addQueryParam(name, String.valueOf(value), encoded);
            }
        }
    }

    static final class QueryMap<T> extends ParameterHandler<T> {
        private final boolean encoded;

        QueryMap(boolean encoded) {
            this.encoded = encoded;
        }

        void apply(RequestBuilder builder, T value) {
            if (value == null) {
                return;
            }
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("@QueryMap parameter must be a Map.");
            }
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                if (k != null && v != null) {
                    builder.addQueryParam(String.valueOf(k), String.valueOf(v), encoded);
                }
            }
        }
    }

    static final class Header<T> extends ParameterHandler<T> {
        private final String name;

        Header(String name) {
            this.name = name;
        }

        void apply(RequestBuilder builder, T value) {
            if (value != null) {
                builder.addHeader(name, String.valueOf(value));
            }
        }
    }

    static final class HeaderMap<T> extends ParameterHandler<T> {
        void apply(RequestBuilder builder, T value) {
            if (value == null) {
                return;
            }
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("@HeaderMap parameter must be a Map.");
            }
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                if (k != null && v != null) {
                    builder.addHeader(String.valueOf(k), String.valueOf(v));
                }
            }
        }
    }

    static final class Url extends ParameterHandler<Object> {
        void apply(RequestBuilder builder, Object value) {
            if (value == null) {
                throw new IllegalArgumentException("@Url parameter value must not be null.");
            }
            builder.setRelativeUrl(String.valueOf(value));
        }
    }

    static final class Body<T> extends ParameterHandler<T> {
        private final Converter<T, RequestBody> converter;

        Body(Converter<T, RequestBody> converter) {
            this.converter = converter;
        }

        void apply(RequestBuilder builder, T value) throws IOException {
            if (value == null) {
                throw new IllegalArgumentException("@Body parameter value must not be null.");
            }
            builder.setBody(converter.convert(value));
        }
    }

    static final class Field<T> extends ParameterHandler<T> {
        private final String name;
        private final boolean encoded;

        Field(String name, boolean encoded) {
            this.name = name;
            this.encoded = encoded;
        }

        void apply(RequestBuilder builder, T value) {
            if (value != null) {
                builder.addFormField(name, String.valueOf(value), encoded);
            }
        }
    }

    static final class FieldMap<T> extends ParameterHandler<T> {
        private final boolean encoded;

        FieldMap(boolean encoded) {
            this.encoded = encoded;
        }

        void apply(RequestBuilder builder, T value) {
            if (value == null) {
                return;
            }
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("@FieldMap parameter must be a Map.");
            }
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                if (k != null && v != null) {
                    builder.addFormField(String.valueOf(k), String.valueOf(v), encoded);
                }
            }
        }
    }

    static final class Part<T> extends ParameterHandler<T> {
        private final String name;

        Part(String name) {
            this.name = name;
        }

        void apply(RequestBuilder builder, T value) {
            if (value == null) {
                return;
            }
            if (value instanceof RequestBody) {
                builder.addPart(MultipartBody.Part.create(name, null, (RequestBody) value));
            } else {
                builder.addPart(MultipartBody.Part.create(name, String.valueOf(value)));
            }
        }
    }

    static final class PartMap<T> extends ParameterHandler<T> {
        void apply(RequestBuilder builder, T value) {
            if (value == null) {
                return;
            }
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("@PartMap parameter must be a Map.");
            }
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                if (k == null || v == null) {
                    continue;
                }
                if (v instanceof RequestBody) {
                    builder.addPart(MultipartBody.Part.create(String.valueOf(k), null, (RequestBody) v));
                } else {
                    builder.addPart(MultipartBody.Part.create(String.valueOf(k), String.valueOf(v)));
                }
            }
        }
    }
}
