package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PresenceRestTest extends BaseIntegrationTest {

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
    void getRoomMemberPresence_returnsMembersWithStatus() throws Exception {
        UserInfo user1 = signUp("pres1a@test.com", "pres1a");
        UserInfo user2 = signUp("pres1b@test.com", "pres1b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"presence-room-1","description":"test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user2.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomId + "/members/presence").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("OFFLINE"))
                .andExpect(jsonPath("$[1].status").value("OFFLINE"));
    }

    @Test
    void getRoomMemberPresence_nonMember_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("pres2a@test.com", "pres2a");
        UserInfo user2 = signUp("pres2b@test.com", "pres2b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"presence-room-2","description":"test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/rooms/" + roomId + "/members/presence").cookie(user2.cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoomMemberPresence_notAuthenticated_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("pres3@test.com", "pres3");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"presence-room-3","description":"test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/rooms/" + roomId + "/members/presence"))
                .andExpect(status().isForbidden());
    }
}
