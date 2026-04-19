package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectMessageTest extends BaseIntegrationTest {

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

    private Long makeFriends(UserInfo user1, UserInfo user2) throws Exception {
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
        return friendshipId;
    }

    @Test
    void createDM_withFriend_success() throws Exception {
        UserInfo user1 = signUp("dm1a@test.com", "dm1a");
        UserInfo user2 = signUp("dm1b@test.com", "dm1b");
        makeFriends(user1, user2);

        mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DIRECT"));
    }

    @Test
    void createDM_idempotent() throws Exception {
        UserInfo user1 = signUp("dm2a@test.com", "dm2a");
        UserInfo user2 = signUp("dm2b@test.com", "dm2b");
        makeFriends(user1, user2);

        MvcResult result1 = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("id").asText();

        MvcResult result2 = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId2 = objectMapper.readTree(result2.getResponse().getContentAsString()).get("id").asText();

        // Same room returned
        assert roomId1.equals(roomId2);
    }

    @Test
    void createDM_bothDirections_sameRoom() throws Exception {
        UserInfo user1 = signUp("dm3a@test.com", "dm3a");
        UserInfo user2 = signUp("dm3b@test.com", "dm3b");
        makeFriends(user1, user2);

        MvcResult result1 = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("id").asText();

        MvcResult result2 = mockMvc.perform(post("/api/direct-messages/" + user1.id).cookie(user2.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId2 = objectMapper.readTree(result2.getResponse().getContentAsString()).get("id").asText();

        assert roomId1.equals(roomId2);
    }

    @Test
    void createDM_notFriends_returnsConflict() throws Exception {
        UserInfo user1 = signUp("dm4a@test.com", "dm4a");
        UserInfo user2 = signUp("dm4b@test.com", "dm4b");

        mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void createDM_blocked_returnsConflict() throws Exception {
        UserInfo user1 = signUp("dm5a@test.com", "dm5a");
        UserInfo user2 = signUp("dm5b@test.com", "dm5b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void sendMessage_inBlockedDM_returnsConflict() throws Exception {
        UserInfo user1 = signUp("dm6a@test.com", "dm6a");
        UserInfo user2 = signUp("dm6b@test.com", "dm6b");
        makeFriends(user1, user2);

        // Create DM
        MvcResult result = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Block user2
        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        // Try to send message — should fail
        mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"Hello after block"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void createDM_withSelf_returns400() throws Exception {
        UserInfo user1 = signUp("dm7@test.com", "dm7");

        mockMvc.perform(post("/api/direct-messages/" + user1.id).cookie(user1.cookie))
                .andExpect(status().isBadRequest());
    }
}
