package com.careercompass.security.config;

import com.careercompass.security.oauth.GitHubOAuth2UserService;
import com.careercompass.security.web.ApiAccessDeniedHandler;
import com.careercompass.security.web.ApiAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("prod")
@EnableWebSecurity
public class OAuthSessionSecurityConfig {

    @Bean
    SecurityFilterChain oauthSessionSecurityFilterChain(
            HttpSecurity http,
            GitHubOAuth2UserService gitHubOAuth2UserService,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/oauth2/**",
                                "/login/**",
                                "/api/v1/auth/me",
                                "/api/v1/auth/csrf",
                                "/",
                                "/index.html",
                                "/app.js",
                                "/styles.css"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(gitHubOAuth2UserService))
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, exception) ->
                                authenticationEntryPoint.commence(
                                        request, response, exception)))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
