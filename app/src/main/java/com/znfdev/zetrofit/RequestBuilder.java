package com.znfdev.zetrofit;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Accumulates the pieces of a single HTTP request as parameters are applied. */
final class RequestBuilder {

    private final String method;
    private final String baseUrl;
    private String relativeUrl;
    private final StringBuilder query = new StringBuilder();
    private final Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
    private final boolean isFormEncoded;
    private final boolean isMultipart;
    private final StringBuilder formBody;
    private final List<MultipartBody.Part> multipartParts;
    private final String boundary;
    private RequestBody body;

    RequestBuilder(String method, String baseUrl, String relativeUrl, boolean isFormEncoded, boolean isMultipart) {
        this.method = method;
        this.baseUrl = baseUrl;
        this.relativeUrl = relativeUrl;
        this.isFormEncoded = isFormEncoded;
        this.isMultipart = isMultipart;
        this.formBody = isFormEncoded ? new StringBuilder() : null;
        this.multipartParts = isMultipart ? new ArrayList<MultipartBody.Part>() : null;
        this.boundary = isMultipart ? "ZetrofitBoundary" + UUID.randomUUID().toString() : null;
    }

    void addHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<String>();
            headers.put(name, values);
        }
        values.add(value);
    }

    void setRelativeUrl(String url) {
        this.relativeUrl = url;
    }

    void addPathParam(String name, String value, boolean encoded) {
        if (relativeUrl == null) {
            throw new IllegalStateException("@Url parameter must be set before @Path parameters can be applied.");
        }
        String encodedValue = encoded ? value : encode(value, false);
        relativeUrl = relativeUrl.replace("{" + name + "}", encodedValue);
    }

    void addQueryParam(String name, String value, boolean encoded) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(encoded ? name : encode(name, false));
        query.append('=');
        query.append(encoded ? value : encode(value, false));
    }

    void addFormField(String name, String value, boolean encoded) {
        if (formBody.length() > 0) {
            formBody.append('&');
        }
        formBody.append(encoded ? name : encode(name, false));
        formBody.append('=');
        formBody.append(encoded ? value : encode(value, false));
    }

    void addPart(MultipartBody.Part part) {
        multipartParts.add(part);
    }

    void setBody(RequestBody body) {
        this.body = body;
    }

    private static String encode(String s, boolean isFormValue) {
        try {
            String encoded = URLEncoder.encode(s, "UTF-8");
            return isFormValue ? encoded : encoded.replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    String method() {
        return method;
    }

    Map<String, List<String>> headers() {
        return headers;
    }

    RequestBody body() throws IOException {
        if (isFormEncoded) {
            return RequestBody.create("application/x-www-form-urlencoded; charset=UTF-8", formBody.toString());
        }
        if (isMultipart) {
            return MultipartBody.create(boundary, multipartParts);
        }
        return body;
    }

    URL url() throws MalformedURLException {
        String url = relativeUrl == null ? "" : relativeUrl;
        if (query.length() > 0) {
            url += (url.indexOf('?') == -1 ? "?" : "&") + query;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return new URL(url);
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String rel = url.startsWith("/") ? url.substring(1) : url;
        return new URL(base + rel);
    }
}
