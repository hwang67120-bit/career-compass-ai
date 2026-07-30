package com.careercompass.security.currentuser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

class TestCurrentUserProviderProfileTest {

    private static final UUID TEST_USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void testCurrentUserProvider_withTestProfile_usesConfiguredUser() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("test");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "test.user-id=" + TEST_USER_ID);
            context.register(TestCurrentUserProvider.class);
            context.refresh();
            assertThat(context.getBean(CurrentUserProvider.class).getCurrentUserId()).isEqualTo(TEST_USER_ID);
        }
    }

    @Test
    void testCurrentUserProvider_withDevelopmentProfile_isNotCreated() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.register(TestCurrentUserProvider.class);
            context.refresh();
            assertThat(context.getBeansOfType(TestCurrentUserProvider.class)).isEmpty();
        }
    }
}