package com.locpham.bookstore.catalogservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            MdcRequestFilter mdcRequestFilter)
            throws Exception {
        return http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(EndpointRequest.toAnyEndpoint())
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/", "/books/**")
                                        .permitAll()
                                        .anyRequest()
                                        .hasRole("employee"))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)))
                .addFilterAfter(mdcRequestFilter, BearerTokenAuthenticationFilter.class)
                .sessionManagement(
                        sessionManagement ->
                                sessionManagement.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    MdcRequestFilter mdcRequestFilter() {
        return new MdcRequestFilter();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            @Value("${polar.keycloak.client-id:}") String clientId) {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakJwtAuthoritiesConverter(clientId));
        return authenticationConverter;
    }
}
