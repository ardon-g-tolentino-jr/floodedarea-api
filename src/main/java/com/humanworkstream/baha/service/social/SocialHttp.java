package com.humanworkstream.baha.service.social;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Shared short-timeout {@link RestClient} for outbound calls to Facebook / X — a stuck social
 *  network must never hang the retry sweep or the (already off-request-thread) async publish. */
final class SocialHttp {
    private SocialHttp() {}

    static RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return RestClient.builder().requestFactory(factory).build();
    }
}
