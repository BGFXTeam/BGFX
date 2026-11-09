package com.znfdev.zetrofit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class RealCall<T> implements Call<T> {

    private final Zetrofit zetrofit;
    private final ServiceMethod<T> serviceMethod;
    private final Object[] args;
    private volatile boolean canceled;
    private volatile boolean executed;
    private volatile HttpURLConnection rawConnection;

    RealCall(Zetrofit zetrofit, ServiceMethod<T> serviceMethod, Object[] args) {
        this.zetrofit = zetrofit;
        this.serviceMethod = serviceMethod;
        this.args = args;
    }

    public synchronized Response<T> execute() throws IOException {
        if (executed) {
            throw new IllegalStateException("Already executed.");
        }
        executed = true;
        return doExecute();
    }

    public void enqueue(final Callback<T> callback) {
        Utils.checkNotNull(callback, "callback == null");
        synchronized (this) {
            if (executed) {
                throw new IllegalStateException("Already executed.");
            }
            executed = true;
        }
        final RealCall<T> self = this;
        zetrofit.callExecutor().execute(new Runnable() {
            public void run() {
                Response<T> response = null;
                Throwable failure = null;
                try {
                    response = doExecute();
                } catch (Throwable t) {
                    failure = t;
                }
                if (canceled) {
                    return;
                }
                final Response<T> finalResponse = response;
                final Throwable finalFailure = failure;
                Executor callbackExecutor = zetrofit.callbackExecutor();
                Runnable deliver = new Runnable() {
                    public void run() {
                        if (finalFailure != null) {
                            callback.onFailure(self, finalFailure);
                        } else {
                            callback.onResponse(self, finalResponse);
                        }
                    }
                };
                if (callbackExecutor != null) {
                    callbackExecutor.execute(deliver);
                } else {
                    deliver.run();
                }
            }
        });
    }

    public boolean isExecuted() {
        return executed;
    }

    public void cancel() {
        canceled = true;
        HttpURLConnection connection = rawConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Call<T> clone() {
        return new RealCall<T>(zetrofit, serviceMethod, args);
    }

    @SuppressWarnings("unchecked")
    private Response<T> doExecute() throws IOException {
        RequestBuilder requestBuilder = serviceMethod.toRequestBuilder(args);
        URL url = requestBuilder.url();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        rawConnection = connection;
        connection.setRequestMethod(requestBuilder.method());
        connection.setConnectTimeout(zetrofit.connectTimeoutMillis());
        connection.setReadTimeout(zetrofit.readTimeoutMillis());
        connection.setInstanceFollowRedirects(true);

        Map<String, List<String>> headers = requestBuilder.headers();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            List<String> values = entry.getValue();
            for (int i = 0; i < values.size(); i++) {
                connection.addRequestProperty(entry.getKey(), values.get(i));
            }
        }

        RequestBody body = requestBuilder.body();
        boolean methodAllowsBody = !"GET".equals(requestBuilder.method()) && !"HEAD".equals(requestBuilder.method());
        if (body != null && methodAllowsBody) {
            if (body.contentType() != null && headers.get("Content-Type") == null) {
                connection.setRequestProperty("Content-Type", body.contentType());
            }
            byte[] content = body.content();
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(content.length);
            OutputStream os = connection.getOutputStream();
            try {
                os.write(content);
                os.flush();
            } finally {
                os.close();
            }
        }

        int code;
        try {
            code = connection.getResponseCode();
        } catch (IOException e) {
            if (canceled) {
                throw new IOException("Canceled", e);
            }
            throw e;
        }
        String message = connection.getResponseMessage();
        Map<String, List<String>> responseHeaders = connection.getHeaderFields();
        String contentType = connection.getContentType();

        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] bytes = stream == null ? new byte[0] : readAll(stream);
        ResponseBody responseBody = ResponseBody.create(contentType, bytes);

        if (code >= 200 && code < 300) {
            T converted;
            if (serviceMethod.responseConverter != null) {
                converted = serviceMethod.responseConverter.convert(responseBody);
            } else {
                converted = (T) responseBody;
            }
            return Response.success(converted, code, message, responseHeaders);
        } else {
            return Response.error(responseBody, code, message, responseHeaders);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        try {
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }
}
