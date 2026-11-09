package com.znfdev.zetrofit;

import java.io.IOException;

/**
 * A prepared call to a remote endpoint that yields a {@link Response}&lt;T&gt;.
 * Instances are one-shot: calling {@link #execute()} or {@link #enqueue(Callback)}
 * more than once will throw. Use {@link #clone()} to make another usable copy.
 */
public interface Call<T> extends Cloneable {

    /** Executes the request synchronously on the calling thread. */
    Response<T> execute() throws IOException;

    /** Schedules the request to run in the background, delivering the result to {@code callback}. */
    void enqueue(Callback<T> callback);

    /** True if {@link #execute()} or {@link #enqueue(Callback)} was already called. */
    boolean isExecuted();

    /** Attempts to cancel this call. Has no effect if it already completed. */
    void cancel();

    /** True if {@link #cancel()} was called. */
    boolean isCanceled();

    /** Returns a fresh, unexecuted copy of this call. */
    Call<T> clone();
}
