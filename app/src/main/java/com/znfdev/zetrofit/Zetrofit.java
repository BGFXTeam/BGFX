package com.znfdev.zetrofit;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Zetrofit turns an annotated Java interface into a working HTTP client, in the spirit
 * of Square's Retrofit but written from scratch, dependency-free, and Java 7 compatible
 * (no lambdas, no default interface methods).
 *
 * <pre>
 * Zetrofit zetrofit = new Zetrofit.Builder()
 *         .baseUrl("https://api.example.com/")
 *         .addConverterFactory(GsonConverterFactory.create())
 *         .build();
 *
 * MyService service = zetrofit.create(MyService.class);
 * </pre>
 */
public final class Zetrofit {

    private final String baseUrl;
    private final List<Converter.Factory> converterFactories;
    private final ExecutorService callExecutor;
    private final Executor callbackExecutor;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Map<Method, ServiceMethod<?>> serviceMethodCache = new HashMap<Method, ServiceMethod<?>>();

    private Zetrofit(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.converterFactories = Collections.unmodifiableList(new ArrayList<Converter.Factory>(builder.converterFactories));
        this.callExecutor = builder.callExecutor != null ? builder.callExecutor : Executors.newCachedThreadPool();
        this.callbackExecutor = builder.callbackExecutor != null ? builder.callbackExecutor : Platform.get().defaultCallbackExecutor();
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
    }

    public String baseUrl() {
        return baseUrl;
    }

    ExecutorService callExecutor() {
        return callExecutor;
    }

    Executor callbackExecutor() {
        return callbackExecutor;
    }

    int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    /** Creates an implementation of the given API interface. */
    @SuppressWarnings("unchecked")
    public <S> S create(final Class<S> service) {
        if (!service.isInterface()) {
            throw new IllegalArgumentException("API declarations must be interfaces.");
        }
        return (S) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(this, args);
                        }
                        ServiceMethod<Object> serviceMethod = loadServiceMethod(method);
                        return new RealCall<Object>(Zetrofit.this, serviceMethod, args);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private ServiceMethod<Object> loadServiceMethod(Method method) {
        synchronized (serviceMethodCache) {
            ServiceMethod<?> result = serviceMethodCache.get(method);
            if (result == null) {
                result = ServiceMethod.parse(this, method);
                serviceMethodCache.put(method, result);
            }
            return (ServiceMethod<Object>) result;
        }
    }

    @SuppressWarnings("unchecked")
    <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
        for (int i = 0; i < converterFactories.size(); i++) {
            Converter<ResponseBody, ?> converter = converterFactories.get(i).responseBodyConverter(type, annotations, this);
            if (converter != null) {
                return (Converter<ResponseBody, T>) converter;
            }
        }
        return (Converter<ResponseBody, T>) BuiltInConverters.toResponseBodyConverter(type);
    }

    @SuppressWarnings("unchecked")
    <T> Converter<T, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        for (int i = 0; i < converterFactories.size(); i++) {
            Converter<?, RequestBody> converter =
                    converterFactories.get(i).requestBodyConverter(type, parameterAnnotations, methodAnnotations, this);
            if (converter != null) {
                return (Converter<T, RequestBody>) converter;
            }
        }
        return (Converter<T, RequestBody>) BuiltInConverters.toRequestBodyConverter(type);
    }

    public static final class Builder {
        private String baseUrl;
        private final List<Converter.Factory> converterFactories = new ArrayList<Converter.Factory>();
        private ExecutorService callExecutor;
        private Executor callbackExecutor;
        private int connectTimeoutMillis = 15000;
        private int readTimeoutMillis = 15000;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Utils.checkNotNull(baseUrl, "baseUrl == null");
            return this;
        }

        public Builder addConverterFactory(Converter.Factory factory) {
            converterFactories.add(Utils.checkNotNull(factory, "factory == null"));
            return this;
        }

        /** Overrides the background {@link ExecutorService} used to run enqueued calls. */
        public Builder callExecutor(ExecutorService executor) {
            this.callExecutor = executor;
            return this;
        }

        /** Overrides where callback results are delivered. Defaults to the Android main thread when available. */
        public Builder callbackExecutor(Executor executor) {
            this.callbackExecutor = executor;
            return this;
        }

        public Builder connectTimeout(int millis) {
            this.connectTimeoutMillis = millis;
            return this;
        }

        public Builder readTimeout(int millis) {
            this.readTimeoutMillis = millis;
            return this;
        }

        public Zetrofit build() {
            if (baseUrl == null) {
                throw new IllegalStateException("Base URL required.");
            }
            return new Zetrofit(this);
        }
    }
}
