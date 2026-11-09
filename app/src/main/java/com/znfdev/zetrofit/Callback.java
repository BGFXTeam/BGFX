package com.znfdev.zetrofit;

/** Communicates responses from a server or offline requests. */
public interface Callback<T> {

    /** Called when the HTTP response was received, successful or not. */
    void onResponse(Call<T> call, Response<T> response);

    /** Called when the request could not be executed (network failure, conversion error, etc). */
    void onFailure(Call<T> call, Throwable t);
}
