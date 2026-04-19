package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BlockTest extends BaseIntegrationTest {

    private record UserInfo(Long id, Cookie cookie) {}

    private UserInfo signUp(String email, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","username":"%s","password":"password123","displayName":"Test User"}
                        """.formatted(email, username)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        return new UserInfo(id, cookie);
    }

    @Test
    void blockUser_success() throws Exception {
        UserInfo user1 = signUp("blk1a@test.com", "blk1a");
        UserInfo user2 = signUp("blk1b@test.com", "blk1b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockedUsername").value("blk1b"));
    }

    @Test
    void blockUser_self_returns400() throws Exception {
        UserInfo user1 = signUp("blk2@test.com", "blk2");

        mockMvc.perform(post("/api/users/" + user1.id + "/block")
                        .cookie(user1.cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blockUser_removesFriendship() throws Exception {
        UserInfo user1 = signUp("blk3a@test.com", "blk3a");
        UserInfo user2 = signUp("blk3b@test.com", "blk3b");

        // Become friends
        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept").cookie(user2.cookie))
                .andExpect(status().isOk());

        // Block should remove friendship
        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/friends").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blockUser_preventsFriendRequest() throws Exception {
        UserInfo user1 = signUp("blk4a@test.com", "blk4a");
        UserInfo user2 = signUp("blk4b@test.com", "blk4b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user1.id)))
                .andExpect(status().isConflict());
    }

    @Test
    void unblockUser_success() throws Exception {
        UserInfo user1 = signUp("blk5a@test.com", "blk5a");
        UserInfo user2 = signUp("blk5b@test.com", "blk5b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/blocks").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getBlockedUsers_returnsList() throws Exception {
        UserInfo user1 = signUp("blk6a@test.com", "blk6a");
        UserInfo user2 = signUp("blk6b@test.com", "blk6b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/blocks").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].blockedUsername").value("blk6b"));
    }
}
