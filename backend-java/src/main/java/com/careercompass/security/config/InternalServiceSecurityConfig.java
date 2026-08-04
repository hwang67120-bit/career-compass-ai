package com.careercompass.security.config;

import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.security.internal.InternalServiceErrorResponseWriter;
import com.careercompass.security.internal.InternalServiceTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class InternalServiceSecurityConfig {

    /**
     * 기능: 내부 API에 세션과 CSRF 대신 공유 내부 서비스 토큰 인증을 적용한다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain internalServiceSecurityFilterChain(
            HttpSecurity http,
            PythonWorkerProperties properties,
            InternalServiceErrorResponseWriter errorResponseWriter
    ) throws Exception {
        InternalServiceTokenFilter tokenFilter =
                new InternalServiceTokenFilter(
                        properties,
                        errorResponseWriter
                );
        return http
                .securityMatcher("/internal/v1/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .anyRequest().permitAll())
                .addFilterBefore(
                        tokenFilter,
                        AnonymousAuthenticationFilter.class
                )
                .build();
    }
}
