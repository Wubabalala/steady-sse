package io.github.wubabalala.steadysse.cancel;

import okhttp3.Call;

import java.util.Objects;

/**
 * CancellableCall adapter for OkHttp's {@link Call}.
 */
public class OkHttpCancellableCall implements CancellableCall {
    private final Call call;

    public OkHttpCancellableCall(Call call) {
        this.call = Objects.requireNonNull(call, "OkHttp Call must not be null");
    }

    @Override
    public void cancel() {
        call.cancel();
    }

    @Override
    public boolean isCancelled() {
        return call.isCanceled();
    }
}
