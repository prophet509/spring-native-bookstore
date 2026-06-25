package com.locpham.bookstore.orderservice.bootstrap.config;

import io.micrometer.observation.ObservationRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(
            ObservationRegistry observationRegistry,
            ResourceLoader resourceLoader,
            @Value("${polar.http-client.mtls.enabled:false}") boolean mtlsEnabled,
            @Value("${polar.http-client.mtls.key-store:}") String keyStoreLocation,
            @Value("${polar.http-client.mtls.key-store-password:}") String keyStorePassword,
            @Value("${polar.http-client.mtls.key-store-type:PKCS12}") String keyStoreType,
            @Value("${polar.http-client.mtls.trust-store:}") String trustStoreLocation,
            @Value("${polar.http-client.mtls.trust-store-password:}") String trustStorePassword,
            @Value("${polar.http-client.mtls.trust-store-type:PKCS12}") String trustStoreType)
            throws Exception {
        HttpClient httpClient =
                HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .responseTimeout(Duration.ofMillis(5000))
                        .doOnConnected(
                                conn ->
                                        conn.addHandlerLast(
                                                        new ReadTimeoutHandler(
                                                                10, TimeUnit.SECONDS))
                                                .addHandlerLast(
                                                        new WriteTimeoutHandler(
                                                                10, TimeUnit.SECONDS)));

        if (mtlsEnabled) {
            SslContext sslContext =
                    mtlsSslContext(
                            resourceLoader,
                            keyStoreLocation,
                            keyStorePassword,
                            keyStoreType,
                            trustStoreLocation,
                            trustStorePassword,
                            trustStoreType);
            httpClient = httpClient.secure(ssl -> ssl.sslContext(sslContext));
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .observationRegistry(observationRegistry);
    }

    private SslContext mtlsSslContext(
            ResourceLoader resourceLoader,
            String keyStoreLocation,
            String keyStorePassword,
            String keyStoreType,
            String trustStoreLocation,
            String trustStorePassword,
            String trustStoreType)
            throws Exception {
        if (keyStoreLocation.isBlank() || trustStoreLocation.isBlank()) {
            throw new IllegalStateException(
                    "mTLS WebClient is enabled but key-store/trust-store locations are missing");
        }

        KeyStore keyStore =
                loadStore(resourceLoader, keyStoreLocation, keyStorePassword, keyStoreType);
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        KeyStore trustStore =
                loadStore(resourceLoader, trustStoreLocation, trustStorePassword, trustStoreType);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        return SslContextBuilder.forClient()
                .keyManager(keyManagerFactory)
                .trustManager(trustManagerFactory)
                .build();
    }

    private KeyStore loadStore(
            ResourceLoader resourceLoader, String location, String password, String type)
            throws Exception {
        Resource resource = resourceLoader.getResource(location);
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream inputStream = resource.getInputStream()) {
            keyStore.load(inputStream, password.toCharArray());
        }
        return keyStore;
    }
}
