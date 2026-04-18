package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordChangeTest extends BaseIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String NEW_PASSWORD = "newpassword456";

    private Cookie signUpAndGetCookie(String email, String username) throws Exception {
        String body = """
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "%s",
                    "displayName": "Test User"
                }
                """.formatted(email, username, PASSWORD);

        Cookie cookie = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void changePassword_validRequest_returns200() throws Exception {
        Cookie cookie = signUpAndGetCookie("chpw1@test.com", "chpw1");

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(PASSWORD, NEW_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_canSignInWithNewPassword() throws Exception {
        Cookie cookie = signUpAndGetCookie("chpw2@test.com", "chpw2");

        String changeBody = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(PASSWORD, NEW_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody))
                .andExpect(status().isOk());

        String signInBody = """
                {
                    "email": "chpw2@test.com",
                    "password": "%s"
                }
                """.formatted(NEW_PASSWORD);

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400() throws Exception {
        Cookie cookie = signUpAndGetCookie("chpw3@test.com", "chpw3");

        String body = """
                {
                    "currentPassword": "wrongpassword",
                    "newPassword": "%s"
                }
                """.formatted(NEW_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changePassword_shortNewPassword_returns400() throws Exception {
        Cookie cookie = signUpAndGetCookie("chpw4@test.com", "chpw4");

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "short"
                }
                """.formatted(PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_notAuthenticated_returns403() throws Exception {
        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(PASSWORD, NEW_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
