package io.hackfest;

import io.vertx.core.http.RequestOptions;

import java.util.Base64;

public record CryptoHeaders(
        String deviceId,
        long timestamp,
        byte[] signature
) {
    public void applyTo(RequestOptions options) {
        options.addHeader("pos-device-id", deviceId);
        options.addHeader("pos-sign-timestamp", String.valueOf(timestamp));
        options.addHeader("pos-signature", Base64.getEncoder().encodeToString(signature));
    }
}
