package com.aegismesh.network;

/**
 * Generic async callback for ApiClient service calls. onSuccess/onError may be
 * invoked from a background thread - callers are expected to marshal to the
 * UI thread themselves (as LoginActivity/ProfileActivity already do via
 * runOnUiThread).
 */
public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(Throwable error);
}
