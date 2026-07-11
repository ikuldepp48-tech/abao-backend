package com.geihou.module.system.controller.admin.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GeihouAdminAuthUnavailableControllerWebMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GeihouAdminAuthUnavailableController())
                .build();
    }

    @Test
    void loginShouldReturnHttp503WithCode1008() throws Exception {
        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1008))
                .andExpect(jsonPath("$.msg").value("AUTH_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void twoFactorVerifyShouldReturnHttp503WithCode1008() throws Exception {
        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1008))
                .andExpect(jsonPath("$.msg").value("AUTH_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void codeShouldBeNumeric1008NotString() throws Exception {
        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(1008));
    }
}
