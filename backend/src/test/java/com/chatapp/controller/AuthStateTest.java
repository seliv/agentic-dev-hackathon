package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthStateTest extends BaseIntegrationTest {

    private Cookie signUpAndGetCookie(String email, String username) throws Exception {
        String body = """
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "password123",
                    "displayName": "Test User"
                }
                """.formatted(email, username);

        Cookie sessionCookie = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    @Test
    void me_withValidSession_returnsUser() throws Exception {
        Cookie sessionCookie = signUpAndGetCookie("authstate1@test.com", "authstate1");

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("authstate1@test.com"))
                .andExpect(jsonPath("$.username").value("authstate1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        Cookie sessionCookie = signUpAndGetCookie("authstate3@test.com", "authstate3");

        Cookie meCookie = mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("authstate3@test.com"))
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        Cookie activeCookie = meCookie != null ? meCookie : sessionCookie;

        mockMvc.perform(post("/api/auth/logout").cookie(activeCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").cookie(activeCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_afterSignIn_returnsUser() throws Exception {
        signUpAndGetCookie("authstate4@test.com", "authstate4");

        String signinBody = """
                {
                    "email": "authstate4@test.com",
                    "password": "password123"
                }
                """;

        Cookie signinCookie = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(signinCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(signinCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("authstate4@test.com"))
                .andExpect(jsonPath("$.username").value("authstate4"));
    }
}
