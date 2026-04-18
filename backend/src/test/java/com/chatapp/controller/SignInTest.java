package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SignInTest extends BaseIntegrationTest {

    private jakarta.servlet.http.Cookie signUpAndGetCookie(String email, String username) throws Exception {
        String body = """
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "password123",
                    "displayName": "Test User"
                }
                """.formatted(email, username);

        return mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
    }

    @Test
    void signIn_validCredentials_returns200WithUser() throws Exception {
        signUpAndGetCookie("signin1@test.com", "signinuser1");

        String body = """
                {
                    "email": "signin1@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("signin1@test.com"))
                .andExpect(jsonPath("$.username").value("signinuser1"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signIn_createsSession_canAccessMe() throws Exception {
        signUpAndGetCookie("signin2@test.com", "signinuser2");

        String body = """
                {
                    "email": "signin2@test.com",
                    "password": "password123"
                }
                """;

        jakarta.servlet.http.Cookie sessionCookie = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("signin2@test.com"));
    }

    @Test
    void signIn_wrongPassword_returns401WithMessage() throws Exception {
        signUpAndGetCookie("signin3@test.com", "signinuser3");

        String body = """
                {
                    "email": "signin3@test.com",
                    "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signIn_nonExistentEmail_returns401WithMessage() throws Exception {
        String body = """
                {
                    "email": "nonexistent@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signIn_deletedUser_returns401() throws Exception {
        jakarta.servlet.http.Cookie sessionCookie = signUpAndGetCookie("signin5@test.com", "signinuser5");
        assertThat(sessionCookie).isNotNull();

        String deleteBody = """
                {
                    "password": "password123"
                }
                """;

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody)
                        .cookie(sessionCookie))
                .andExpect(status().isOk());

        String signinBody = """
                {
                    "email": "signin5@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signIn_missingEmail_returns400() throws Exception {
        String body = """
                {
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signIn_missingPassword_returns400() throws Exception {
        String body = """
                {
                    "email": "signin7@test.com"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signIn_invalidEmailFormat_returns400() throws Exception {
        String body = """
                {
                    "email": "not-an-email",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
