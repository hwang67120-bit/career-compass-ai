package com.careercompass.security.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.careercompass.common.config.TimeConfig;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.security.config.OAuthSessionSecurityConfig;
import com.careercompass.security.oauth.GitHubOAuth2UserService;
import com.careercompass.security.web.ApiAccessDeniedHandler;
import com.careercompass.security.web.ApiAuthenticationEntryPoint;
import com.careercompass.security.web.SecurityErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "server.servlet.session.cookie.secure=false"
})
@Import({
        OAuthSessionSecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        SecurityErrorResponseWriter.class,
        ApiResponseFactory.class,
        TimeConfig.class
})
class AuthControllerSecurityTest {

    private static final UUID USER_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubOAuth2UserService gitHubOAuth2UserService;

    @Test
    void getCurrentUser_withoutSession_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void getCurrentUser_withOAuthSession_returnsInternalUserId() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(oauth2Login().oauth2User(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()));
    }

    @Test
    void getCsrfToken_returnsHeaderNameAndToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void protectedApi_withoutSession_returnsUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/project-sources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.errorType").value("UNAUTHORIZED"));
    }

    @Test
    void loginStart_redirectsToGitHubAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        startsWith("https://github.com/login/oauth/authorize")
                ));
    }

    @Test
    void landingPage_withoutSession_isPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void logout_withoutCsrfToken_returnsForbiddenResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(oauth2Login().oauth2User(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorType").value("FORBIDDEN"));
    }

    @Test
    void logout_withSessionAndCsrfToken_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(oauth2Login().oauth2User(principal()))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private static DefaultOAuth2User principal() {
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "userId", USER_ID.toString(),
                        "provider", "GITHUB"
                ),
                "userId"
        );
    }
}
