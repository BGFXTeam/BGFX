package com.znfdev.zson;

/** Configures and creates {@link Zson} instances, mirroring Gson's {@code GsonBuilder}. */
public final class ZsonBuilder {

    private boolean prettyPrinting;
    private boolean serializeNulls;
    private boolean requireExpose;

    public ZsonBuilder setPrettyPrinting() {
        this.prettyPrinting = true;
        return this;
    }

    public ZsonBuilder serializeNulls() {
        this.serializeNulls = true;
        return this;
    }

    /** Only fields annotated with {@code @Expose} will be serialized or deserialized. */
    public ZsonBuilder excludeFieldsWithoutExposeAnnotation() {
        this.requireExpose = true;
        return this;
    }

    public Zson create() {
        return new Zson(prettyPrinting, serializeNulls, requireExpose);
    }
}
