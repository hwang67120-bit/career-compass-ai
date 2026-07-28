package com.careercompass.security.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.careercompass.common.config.TimeConfig;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DevelopmentAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({
        ApiResponseFactory.class,
        TimeConfig.class
})
class DevelopmentAuthControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void getCurrentUser_returnsConfiguredDevelopmentUser() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()));
    }

    @Test
    void landingPage_isAvailableInDevelopmentProfile() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
