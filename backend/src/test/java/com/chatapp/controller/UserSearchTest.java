package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserSearchTest extends BaseIntegrationTest {

    private Cookie signUpAndGetCookie(String email, String username, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","username":"%s","password":"password123","displayName":"%s"}
                        """.formatted(email, username, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void searchUsers_byUsername() throws Exception {
        Cookie cookie = signUpAndGetCookie("us1@test.com", "searchable_user", "Searchable User");
        signUpAndGetCookie("us1b@test.com", "other_user", "Other User");

        mockMvc.perform(get("/api/users/search?q=searchable").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // Excludes self
    }

    @Test
    void searchUsers_byDisplayName() throws Exception {
        signUpAndGetCookie("us2a@test.com", "us2a", "Unique Display Name");
        Cookie searcher = signUpAndGetCookie("us2b@test.com", "us2b", "Searcher");

        mockMvc.perform(get("/api/users/search?q=Unique").cookie(searcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("us2a"));
    }

    @Test
    void searchUsers_caseInsensitive() throws Exception {
        signUpAndGetCookie("us3a@test.com", "CamelUser", "Camel User");
        Cookie searcher = signUpAndGetCookie("us3b@test.com", "us3b", "Searcher");

        mockMvc.perform(get("/api/users/search?q=cameluser").cookie(searcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("CamelUser"));
    }

    @Test
    void searchUsers_tooShort_returnsEmpty() throws Exception {
        Cookie cookie = signUpAndGetCookie("us4@test.com", "us4", "User");

        mockMvc.perform(get("/api/users/search?q=a").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchUsers_excludesSelf() throws Exception {
        Cookie cookie = signUpAndGetCookie("us5@test.com", "selfexclude", "Self Exclude");

        mockMvc.perform(get("/api/users/search?q=selfexclude").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchUsers_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/search?q=test"))
                .andExpect(status().isForbidden());
    }
}
