package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SignUpTest extends BaseIntegrationTest {

    @Test
    void signUp_validRequest_returns201WithUser() throws Exception {
        String body = """
                {
                    "email": "newuser@test.com",
                    "username": "newuser",
                    "password": "password123",
                    "displayName": "New User"
                }
                """;

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@test.com"))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.displayName").value("New User"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signUp_createsSession_canAccessMe() throws Exception {
        String body = """
                {
                    "email": "sessionuser@test.com",
                    "username": "sessionuser",
                    "password": "password123",
                    "displayName": "Session User"
                }
                """;

        jakarta.servlet.http.Cookie sessionCookie = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sessionuser@test.com"));
    }

    @Test
    void signUp_duplicateEmail_returns409() throws Exception {
        String body = """
                {
                    "email": "dupe@test.com",
                    "username": "dupeuser1",
                    "password": "password123",
                    "displayName": "Dupe 1"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String body2 = """
                {
                    "email": "dupe@test.com",
                    "username": "dupeuser2",
                    "password": "password123",
                    "displayName": "Dupe 2"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with this email already exists"));
    }

    @Test
    void signUp_duplicateUsername_returns409() throws Exception {
        String body = """
                {
                    "email": "unique1@test.com",
                    "username": "sameusername",
                    "password": "password123",
                    "displayName": "User 1"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String body2 = """
                {
                    "email": "unique2@test.com",
                    "username": "sameusername",
                    "password": "password123",
                    "displayName": "User 2"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with this username already exists"));
    }

    @Test
    void signUp_missingEmail_returns400() throws Exception {
        String body = """
                {
                    "username": "noemailu",
                    "password": "password123",
                    "displayName": "No Email"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_invalidEmail_returns400() throws Exception {
        String body = """
                {
                    "email": "not-an-email",
                    "username": "bademail",
                    "password": "password123",
                    "displayName": "Bad Email"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_shortPassword_returns400() throws Exception {
        String body = """
                {
                    "email": "shortpw@test.com",
                    "username": "shortpw",
                    "password": "short",
                    "displayName": "Short PW"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_invalidUsername_returns400() throws Exception {
        String body = """
                {
                    "email": "baduser@test.com",
                    "username": "bad user!",
                    "password": "password123",
                    "displayName": "Bad Username"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_usernameTooShort_returns400() throws Exception {
        String body = """
                {
                    "email": "short@test.com",
                    "username": "ab",
                    "password": "password123",
                    "displayName": "Short Username"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
