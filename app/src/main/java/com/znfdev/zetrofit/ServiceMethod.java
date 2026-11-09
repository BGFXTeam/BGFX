package com.znfdev.zetrofit;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.znfdev.zetrofit.http.Body;
import com.znfdev.zetrofit.http.DELETE;
import com.znfdev.zetrofit.http.Field;
import com.znfdev.zetrofit.http.FieldMap;
import com.znfdev.zetrofit.http.FormUrlEncoded;
import com.znfdev.zetrofit.http.GET;
import com.znfdev.zetrofit.http.HEAD;
import com.znfdev.zetrofit.http.Header;
import com.znfdev.zetrofit.http.HeaderMap;
import com.znfdev.zetrofit.http.Headers;
import com.znfdev.zetrofit.http.Multipart;
import com.znfdev.zetrofit.http.OPTIONS;
import com.znfdev.zetrofit.http.PATCH;
import com.znfdev.zetrofit.http.POST;
import com.znfdev.zetrofit.http.PUT;
import com.znfdev.zetrofit.http.Part;
import com.znfdev.zetrofit.http.PartMap;
import com.znfdev.zetrofit.http.Path;
import com.znfdev.zetrofit.http.Query;
import com.znfdev.zetrofit.http.QueryMap;
import com.znfdev.zetrofit.http.Url;

/** Parses a single service-interface method's annotations once, then reuses the result. */
final class ServiceMethod<T> {

    final Zetrofit zetrofit;
    final String httpMethod;
    final String relativeUrl;
    final Map<String, List<String>> staticHeaders;
    final boolean isFormEncoded;
    final boolean isMultipart;
    final ParameterHandler<Object>[] parameterHandlers;
    final Converter<ResponseBody, T> responseConverter;

    private ServiceMethod(Builder<T> builder) {
        this.zetrofit = builder.zetrofit;
        this.httpMethod = builder.httpMethod;
        this.relativeUrl = builder.relativeUrl;
        this.staticHeaders = builder.staticHeaders;
        this.isFormEncoded = builder.isFormEncoded;
        this.isMultipart = builder.isMultipart;
        this.parameterHandlers = builder.parameterHandlers;
        this.responseConverter = builder.responseConverter;
    }

    RequestBuilder toRequestBuilder(Object[] args) throws IOException {
        RequestBuilder rb = new RequestBuilder(httpMethod, zetrofit.baseUrl(), relativeUrl, isFormEncoded, isMultipart);
        for (Map.Entry<String, List<String>> entry : staticHeaders.entrySet()) {
            List<String> values = entry.getValue();
            for (int i = 0; i < values.size(); i++) {
                rb.addHeader(entry.getKey(), values.get(i));
            }
        }
        int count = parameterHandlers.length;
        for (int i = 0; i < count; i++) {
            ParameterHandler<Object> handler = parameterHandlers[i];
            if (handler != null) {
                handler.apply(rb, args == null ? null : args[i]);
            }
        }
        return rb;
    }

    @SuppressWarnings("unchecked")
    static <T> ServiceMethod<T> parse(Zetrofit zetrofit, Method method) {
        Builder<T> builder = new Builder<T>(zetrofit, method);
        builder.parseMethodAnnotations();
        builder.parseParameters();
        return builder.build();
    }

    static final class Builder<T> {
        final Zetrofit zetrofit;
        final Method method;
        final Annotation[] methodAnnotations;
        final Annotation[][] parameterAnnotationsArray;
        final Type[] parameterTypes;

        String httpMethod;
        String relativeUrl;
        boolean hasBody;
        boolean isFormEncoded;
        boolean isMultipart;
        Map<String, List<String>> staticHeaders = new LinkedHashMap<String, List<String>>();
        ParameterHandler<Object>[] parameterHandlers;
        Converter<ResponseBody, T> responseConverter;

        Builder(Zetrofit zetrofit, Method method) {
            this.zetrofit = zetrofit;
            this.method = method;
            this.methodAnnotations = method.getAnnotations();
            this.parameterTypes = method.getGenericParameterTypes();
            this.parameterAnnotationsArray = method.getParameterAnnotations();
        }

        void parseMethodAnnotations() {
            for (int i = 0; i < methodAnnotations.length; i++) {
                Annotation annotation = methodAnnotations[i];
                if (annotation instanceof GET) {
                    parseHttpMethodAndPath("GET", ((GET) annotation).value(), false);
                } else if (annotation instanceof POST) {
                    parseHttpMethodAndPath("POST", ((POST) annotation).value(), true);
                } else if (annotation instanceof PUT) {
                    parseHttpMethodAndPath("PUT", ((PUT) annotation).value(), true);
                } else if (annotation instanceof DELETE) {
                    parseHttpMethodAndPath("DELETE", ((DELETE) annotation).value(), false);
                } else if (annotation instanceof PATCH) {
                    parseHttpMethodAndPath("PATCH", ((PATCH) annotation).value(), true);
                } else if (annotation instanceof HEAD) {
                    parseHttpMethodAndPath("HEAD", ((HEAD) annotation).value(), false);
                } else if (annotation instanceof OPTIONS) {
                    parseHttpMethodAndPath("OPTIONS", ((OPTIONS) annotation).value(), false);
                } else if (annotation instanceof Headers) {
                    String[] headersToParse = ((Headers) annotation).value();
                    if (headersToParse.length == 0) {
                        throw methodError("@Headers annotation is empty.");
                    }
                    for (int j = 0; j < headersToParse.length; j++) {
                        parseHeader(headersToParse[j]);
                    }
                } else if (annotation instanceof FormUrlEncoded) {
                    isFormEncoded = true;
                } else if (annotation instanceof Multipart) {
                    isMultipart = true;
                }
            }
            if (httpMethod == null) {
                throw methodError("Method must have an HTTP annotation such as @GET, @POST, @PUT, @DELETE, @PATCH, @HEAD or @OPTIONS.");
            }
            if (isFormEncoded && isMultipart) {
                throw methodError("Only one of @FormUrlEncoded or @Multipart can be used per method.");
            }
            Type responseType = Utils.getCallResponseType(method.getGenericReturnType());
            responseConverter = zetrofit.responseBodyConverter(responseType, method.getAnnotations());
        }

        private void parseHttpMethodAndPath(String httpMethod, String value, boolean hasBody) {
            if (this.httpMethod != null) {
                throw methodError("Only one HTTP method annotation is allowed. Found: " + this.httpMethod + " and " + httpMethod + ".");
            }
            this.httpMethod = httpMethod;
            this.hasBody = hasBody;
            this.relativeUrl = value;
        }

        private void parseHeader(String header) {
            int colon = header.indexOf(':');
            if (colon == -1 || colon == 0) {
                throw methodError("@Headers value must be in the form \"Name: Value\". Found: \"" + header + "\"");
            }
            String name = header.substring(0, colon);
            String value = header.substring(colon + 1).trim();
            List<String> values = staticHeaders.get(name);
            if (values == null) {
                values = new java.util.ArrayList<String>();
                staticHeaders.put(name, values);
            }
            values.add(value);
        }

        @SuppressWarnings("unchecked")
        void parseParameters() {
            int count = parameterTypes.length;
            parameterHandlers = new ParameterHandler[count];
            for (int i = 0; i < count; i++) {
                Type parameterType = parameterTypes[i];
                Annotation[] parameterAnnotations = parameterAnnotationsArray[i];
                if (parameterAnnotations == null) {
                    continue;
                }
                ParameterHandler<Object> handler = null;
                for (int j = 0; j < parameterAnnotations.length; j++) {
                    Annotation annotation = parameterAnnotations[j];
                    if (annotation instanceof Path) {
                        Path p = (Path) annotation;
                        handler = new ParameterHandler.Path<Object>(p.value(), p.encoded());
                    } else if (annotation instanceof Query) {
                        Query q = (Query) annotation;
                        handler = new ParameterHandler.Query<Object>(q.value(), q.encoded());
                    } else if (annotation instanceof QueryMap) {
                        handler = new ParameterHandler.QueryMap<Object>(((QueryMap) annotation).encoded());
                    } else if (annotation instanceof Header) {
                        handler = new ParameterHandler.Header<Object>(((Header) annotation).value());
                    } else if (annotation instanceof HeaderMap) {
                        handler = new ParameterHandler.HeaderMap<Object>();
                    } else if (annotation instanceof Url) {
                        handler = (ParameterHandler<Object>) (ParameterHandler<?>) new ParameterHandler.Url();
                    } else if (annotation instanceof Body) {
                        Converter<Object, RequestBody> converter =
                                (Converter<Object, RequestBody>) zetrofit.requestBodyConverter(
                                        parameterType, parameterAnnotations, methodAnnotations);
                        handler = new ParameterHandler.Body<Object>(converter);
                    } else if (annotation instanceof Field) {
                        if (!isFormEncoded) {
                            throw methodError("@Field parameters can only be used with @FormUrlEncoded.");
                        }
                        Field f = (Field) annotation;
                        handler = new ParameterHandler.Field<Object>(f.value(), f.encoded());
                    } else if (annotation instanceof FieldMap) {
                        if (!isFormEncoded) {
                            throw methodError("@FieldMap parameters can only be used with @FormUrlEncoded.");
                        }
                        handler = new ParameterHandler.FieldMap<Object>(((FieldMap) annotation).encoded());
                    } else if (annotation instanceof Part) {
                        if (!isMultipart) {
                            throw methodError("@Part parameters can only be used with @Multipart.");
                        }
                        handler = new ParameterHandler.Part<Object>(((Part) annotation).value());
                    } else if (annotation instanceof PartMap) {
                        if (!isMultipart) {
                            throw methodError("@PartMap parameters can only be used with @Multipart.");
                        }
                        handler = new ParameterHandler.PartMap<Object>();
                    }
                }
                parameterHandlers[i] = handler;
            }
        }

        ServiceMethod<T> build() {
            return new ServiceMethod<T>(this);
        }

        private RuntimeException methodError(String message) {
            return new IllegalArgumentException(method.getDeclaringClass().getName() + "." + method.getName() + ": " + message);
        }
    }
}
