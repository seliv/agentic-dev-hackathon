package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionManagementTest extends BaseIntegrationTest {

    private Cookie signUpAndGetCookie(String email, String username) throws Exception {
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

    private Cookie signInAndGetCookie(String email) throws Exception {
        String body = """
                {
                    "email": "%s",
                    "password": "password123"
                }
                """.formatted(email);

        return mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
    }

    private Cookie refreshCookie(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie fresh = result.getResponse().getCookie("SESSION");
        return fresh != null ? fresh : cookie;
    }

    @Test
    void listSessions_returnsCurrentSession() throws Exception {
        Cookie cookie = signUpAndGetCookie("session1@test.com", "sessionuser1");
        cookie = refreshCookie(cookie);

        MvcResult result = mockMvc.perform(get("/api/users/me/sessions").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sessions = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(sessions.isArray()).isTrue();
        assertThat(sessions.size()).isGreaterThanOrEqualTo(1);

        boolean hasCurrentSession = false;
        for (JsonNode session : sessions) {
            if (session.get("current").asBoolean()) {
                hasCurrentSession = true;
                break;
            }
        }
        assertThat(hasCurrentSession).isTrue();
    }

    @Test
    void listSessions_multipleSessionsAfterMultipleSignIns() throws Exception {
        Cookie cookie1 = signUpAndGetCookie("session2@test.com", "sessionuser2");
        assertThat(cookie1).isNotNull();

        Cookie cookie2 = signInAndGetCookie("session2@test.com");
        assertThat(cookie2).isNotNull();

        MvcResult result = mockMvc.perform(get("/api/users/me/sessions").cookie(cookie2))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sessions = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(sessions.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void invalidateSession_removesOtherSession() throws Exception {
        Cookie cookie1 = signUpAndGetCookie("session3@test.com", "sessionuser3");
        assertThat(cookie1).isNotNull();

        Cookie cookie2 = signInAndGetCookie("session3@test.com");

        MvcResult listResult = mockMvc.perform(get("/api/users/me/sessions").cookie(cookie2))
                .andExpect(status().isOk())
                .andReturn();

        Cookie latestCookie = listResult.getResponse().getCookie("SESSION");
        if (latestCookie == null) {
            latestCookie = cookie2;
        }

        JsonNode sessions = objectMapper.readTree(listResult.getResponse().getContentAsString());
        String targetSessionId = null;
        for (JsonNode session : sessions) {
            if (!session.get("current").asBoolean()) {
                targetSessionId = session.get("sessionId").asText();
                break;
            }
        }
        assertThat(targetSessionId).isNotNull();

        mockMvc.perform(post("/api/users/me/sessions/{sessionId}/invalidate", targetSessionId)
                        .cookie(latestCookie))
                .andExpect(status().isOk());
    }

    @Test
    void invalidateSession_nonExistentSession_returns404() throws Exception {
        Cookie cookie = signUpAndGetCookie("session4@test.com", "sessionuser4");

        mockMvc.perform(post("/api/users/me/sessions/{sessionId}/invalidate", "nonexistent-id")
                        .cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSessions_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me/sessions"))
                .andExpect(status().isForbidden());
    }
}
