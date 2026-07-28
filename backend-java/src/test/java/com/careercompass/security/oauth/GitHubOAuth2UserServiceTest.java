package com.careercompass.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.careercompass.user.domain.ExternalIdentity;
import com.careercompass.user.domain.OAuthProvider;
import com.careercompass.user.domain.UserAccount;
import com.careercompass.user.repository.ExternalIdentityRepository;
import com.careercompass.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.Builder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class GitHubOAuth2UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T03:00:00Z");
    private static final UUID USER_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID IDENTITY_ID =
            UUID.fromString("61000000-0000-0000-0000-000000000001");

    private UserAccountRepository userAccountRepository;
    private ExternalIdentityRepository externalIdentityRepository;
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private GitHubOAuth2UserService service;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        externalIdentityRepository = mock(ExternalIdentityRepository.class);
        delegate = mock(OAuth2UserService.class);
        service = new GitHubOAuth2UserService(
                userAccountRepository,
                externalIdentityRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                delegate
        );
    }

    @Test
    void loadUser_withExistingActiveAccount_updatesLoginAndReturnsInternalUserId() {
        OAuth2UserRequest request = oauth2UserRequest("github");
        when(delegate.loadUser(request)).thenReturn(githubUser(583231L));
        UserAccount user = UserAccount.create(USER_ID, NOW.minusSeconds(3600));
        ExternalIdentity identity = ExternalIdentity.create(
                IDENTITY_ID,
                USER_ID,
                OAuthProvider.GITHUB,
                "583231",
                NOW.minusSeconds(3600)
        );
        when(externalIdentityRepository.findByProviderAndProviderUserId(
                OAuthProvider.GITHUB, "583231"))
                .thenReturn(Optional.of(identity));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OAuth2User principal = service.loadUser(request);

        assertThat(principal.getName()).isEqualTo(USER_ID.toString());
        assertThat((String) principal.getAttribute("provider")).isEqualTo("GITHUB");
        assertThat(identity.getLastLoginAt()).isEqualTo(NOW);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void loadUser_withFirstLogin_createsUserAndIdentity() {
        OAuth2UserRequest request = oauth2UserRequest("github");
        when(delegate.loadUser(request)).thenReturn(githubUser(583231L));
        when(externalIdentityRepository.findByProviderAndProviderUserId(
                OAuthProvider.GITHUB, "583231"))
                .thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(externalIdentityRepository.save(any(ExternalIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OAuth2User principal = service.loadUser(request);

        ArgumentCaptor<UserAccount> userCaptor =
                ArgumentCaptor.forClass(UserAccount.class);
        ArgumentCaptor<ExternalIdentity> identityCaptor =
                ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(userAccountRepository).save(userCaptor.capture());
        verify(externalIdentityRepository).save(identityCaptor.capture());

        UserAccount user = userCaptor.getValue();
        ExternalIdentity identity = identityCaptor.getValue();
        assertThat(user.isActive()).isTrue();
        assertThat(identity.getUserId()).isEqualTo(user.getId());
        assertThat(identity.getProviderUserId()).isEqualTo("583231");
        assertThat(principal.getName()).isEqualTo(user.getId().toString());
    }

    @Test
    void loadUser_withoutGitHubNumericId_rejectsLogin() {
        OAuth2UserRequest request = oauth2UserRequest("github");
        OAuth2User githubUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("login", "octocat"),
                "login"
        );
        when(delegate.loadUser(request)).thenReturn(githubUser);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("GitHub 사용자 식별자");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void loadUser_withUnsupportedProvider_rejectsBeforeCallingProvider() {
        OAuth2UserRequest request = oauth2UserRequest("google");

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("지원하지 않는");
        verify(delegate, never()).loadUser(any());
    }

    @Test
    void loadUser_withMissingLinkedAccount_rejectsLogin() {
        OAuth2UserRequest request = oauth2UserRequest("github");
        when(delegate.loadUser(request)).thenReturn(githubUser(583231L));
        ExternalIdentity identity = ExternalIdentity.create(
                IDENTITY_ID, USER_ID, OAuthProvider.GITHUB, "583231", NOW);
        when(externalIdentityRepository.findByProviderAndProviderUserId(
                OAuthProvider.GITHUB, "583231"))
                .thenReturn(Optional.of(identity));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("연결된 사용자");
    }

    private static OAuth2User githubUser(long githubId) {
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", githubId),
                "id"
        );
    }

    private static OAuth2UserRequest oauth2UserRequest(String registrationId) {
        Builder registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName(registrationId);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                NOW,
                NOW.plusSeconds(300)
        );
        return new OAuth2UserRequest(registration.build(), accessToken);
    }
}
