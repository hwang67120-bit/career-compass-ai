package com.careercompass.security.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.careercompass.user.domain.ExternalIdentity;
import com.careercompass.user.domain.OAuthProvider;
import com.careercompass.user.domain.UserAccount;
import com.careercompass.user.repository.ExternalIdentityRepository;
import com.careercompass.user.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"dev", "prod"})
public class GitHubOAuth2UserService
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GITHUB_REGISTRATION_ID = "github";
    private static final String GITHUB_USER_ID_ATTRIBUTE = "id";

    private final UserAccountRepository userAccountRepository;
    private final ExternalIdentityRepository externalIdentityRepository;
    private final Clock clock;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    /**
     * 다중 생성자 중 Spring이 사용할 경로를 지정하고 기본 GitHub OAuth delegate를 구성한다.
     * 테스트에서는 package-private 생성자로 delegate를 대체한다.
     */
    @Autowired
    public GitHubOAuth2UserService(
            UserAccountRepository userAccountRepository,
            ExternalIdentityRepository externalIdentityRepository,
            Clock clock
    ) {
        this(
                userAccountRepository,
                externalIdentityRepository,
                clock,
                new DefaultOAuth2UserService()
        );
    }

    GitHubOAuth2UserService(
            UserAccountRepository userAccountRepository,
            ExternalIdentityRepository externalIdentityRepository,
            Clock clock,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate
    ) {
        this.userAccountRepository = userAccountRepository;
        this.externalIdentityRepository = externalIdentityRepository;
        this.clock = clock;
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {
        requireGitHub(userRequest);
        OAuth2User githubUser = delegate.loadUser(userRequest);
        String providerUserId = extractGitHubUserId(githubUser.getAttributes());
        Instant loggedInAt = clock.instant();

        ExternalIdentity identity = externalIdentityRepository
                .findByProviderAndProviderUserId(OAuthProvider.GITHUB, providerUserId)
                .map(existing -> recordExistingLogin(existing, loggedInAt))
                .orElseGet(() -> createUserAndIdentity(providerUserId, loggedInAt));

        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "userId", identity.getUserId().toString(),
                        "provider", OAuthProvider.GITHUB.name()
                ),
                "userId"
        );
    }

    private ExternalIdentity recordExistingLogin(
            ExternalIdentity identity,
            Instant loggedInAt
    ) {
        UserAccount user = userAccountRepository.findById(identity.getUserId())
                .orElseThrow(() -> oauthFailure(
                        "account_link_missing",
                        "연결된 사용자 계정을 확인할 수 없습니다."
                ));
        if (!user.isActive()) {
            throw oauthFailure("account_inactive", "사용할 수 없는 사용자 계정입니다.");
        }
        identity.recordLogin(loggedInAt);
        return identity;
    }

    private ExternalIdentity createUserAndIdentity(
            String providerUserId,
            Instant createdAt
    ) {
        UserAccount user = userAccountRepository.save(
                UserAccount.create(UUID.randomUUID(), createdAt));
        return externalIdentityRepository.save(ExternalIdentity.create(
                UUID.randomUUID(),
                user.getId(),
                OAuthProvider.GITHUB,
                providerUserId,
                createdAt
        ));
    }

    private static void requireGitHub(OAuth2UserRequest userRequest) {
        String registrationId =
                userRequest.getClientRegistration().getRegistrationId();
        if (!GITHUB_REGISTRATION_ID.equals(registrationId)) {
            throw oauthFailure("unsupported_provider", "지원하지 않는 로그인 제공자입니다.");
        }
    }

    private static String extractGitHubUserId(Map<String, Object> attributes) {
        Object githubUserIdAttribute = attributes.get(GITHUB_USER_ID_ATTRIBUTE);
        if (!(githubUserIdAttribute instanceof Number number) || number.longValue() <= 0) {
            throw oauthFailure(
                    "github_user_id_missing",
                    "GitHub 사용자 식별자를 확인할 수 없습니다."
            );
        }
        return Long.toString(number.longValue());
    }

    private static OAuth2AuthenticationException oauthFailure(
            String errorCode,
            String message
    ) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(errorCode),
                message
        );
    }
}
