package com.aegismesh.network;

public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(Throwable error);
}
