package com.znfdev.zetrofit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Minimal multipart/form-data body builder used for {@code @Multipart} requests. */
final class MultipartBody {

    private MultipartBody() {
    }

    static RequestBody create(final String boundary, final List<Part> parts) {
        return new RequestBody() {
            public String contentType() {
                return "multipart/form-data; boundary=" + boundary;
            }

            public byte[] content() throws IOException {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] dashDash = "--".getBytes("UTF-8");
                byte[] crlf = "\r\n".getBytes("UTF-8");
                for (int i = 0; i < parts.size(); i++) {
                    Part part = parts.get(i);
                    out.write(dashDash);
                    out.write(boundary.getBytes("UTF-8"));
                    out.write(crlf);
                    StringBuilder disposition = new StringBuilder("Content-Disposition: form-data; name=\"")
                            .append(part.name).append("\"");
                    if (part.filename != null) {
                        disposition.append("; filename=\"").append(part.filename).append("\"");
                    }
                    out.write(disposition.toString().getBytes("UTF-8"));
                    out.write(crlf);
                    if (part.body.contentType() != null) {
                        out.write(("Content-Type: " + part.body.contentType()).getBytes("UTF-8"));
                        out.write(crlf);
                    }
                    out.write(crlf);
                    out.write(part.body.content());
                    out.write(crlf);
                }
                out.write(dashDash);
                out.write(boundary.getBytes("UTF-8"));
                out.write(dashDash);
                out.write(crlf);
                return out.toByteArray();
            }
        };
    }

    static final class Part {
        final String name;
        final String filename;
        final RequestBody body;

        Part(String name, String filename, RequestBody body) {
            this.name = name;
            this.filename = filename;
            this.body = body;
        }

        static Part create(String name, String value) {
            return new Part(name, null, RequestBody.create(null, value));
        }

        static Part create(String name, String filename, RequestBody body) {
            return new Part(name, filename, body);
        }
    }
}
