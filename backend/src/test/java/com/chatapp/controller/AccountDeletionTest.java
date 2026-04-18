package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionTest extends BaseIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private UserRepository userRepository;

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
    void deleteAccount_validPassword_returns200() throws Exception {
        Cookie cookie = signUpAndGetCookie("del1@test.com", "del1");

        String body = """
                {"password": "%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccount_setsDeletedAt() throws Exception {
        Cookie cookie = signUpAndGetCookie("del2@test.com", "del2");

        String body = """
                {"password": "%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Optional<User> user = userRepository.findByEmail("del2@test.com");
        assertThat(user).isPresent();
        assertThat(user.get().getDeletedAt()).isNotNull();
    }

    @Test
    void deleteAccount_cannotSignInAfterDeletion() throws Exception {
        Cookie cookie = signUpAndGetCookie("del3@test.com", "del3");

        String deleteBody = """
                {"password": "%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody))
                .andExpect(status().isOk());

        String signInBody = """
                {
                    "email": "del3@test.com",
                    "password": "%s"
                }
                """.formatted(PASSWORD);

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_wrongPassword_returns400() throws Exception {
        Cookie cookie = signUpAndGetCookie("del4@test.com", "del4");

        String body = """
                {"password": "wrongpassword"}
                """;

        mockMvc.perform(delete("/api/users/me")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is incorrect"));
    }

    @Test
    void deleteAccount_notAuthenticated_returns403() throws Exception {
        String body = """
                {"password": "%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
