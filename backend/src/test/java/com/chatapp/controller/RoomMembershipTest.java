package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomMembershipTest extends BaseIntegrationTest {

    private static final String PASSWORD = "password123";

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

    private String createRoom(Cookie cookie, String name) throws Exception {
        String body = """
                {
                    "name": "%s",
                    "description": "Test room"
                }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void joinRoom_success() throws Exception {
        Cookie ownerCookie = signUpAndGetCookie("mem1@test.com", "mem1");
        String roomId = createRoom(ownerCookie, "Join Room");

        Cookie joinerCookie = signUpAndGetCookie("mem2@test.com", "mem2");
        mockMvc.perform(post("/api/rooms/{roomId}/join", roomId)
                        .cookie(joinerCookie))
                .andExpect(status().isOk());

        MvcResult membersResult = mockMvc.perform(get("/api/rooms/{roomId}/members", roomId)
                        .cookie(joinerCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        assertThat(members).hasSize(2);
    }

    @Test
    void joinRoom_alreadyMember_idempotent() throws Exception {
        Cookie cookie = signUpAndGetCookie("mem3@test.com", "mem3");
        String roomId = createRoom(cookie, "Idempotent Room");

        Cookie joinerCookie = signUpAndGetCookie("mem4@test.com", "mem4");

        mockMvc.perform(post("/api/rooms/{roomId}/join", roomId)
                        .cookie(joinerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{roomId}/join", roomId)
                        .cookie(joinerCookie))
                .andExpect(status().isOk());
    }

    @Test
    void leaveRoom_success() throws Exception {
        Cookie ownerCookie = signUpAndGetCookie("mem5@test.com", "mem5");
        String roomId = createRoom(ownerCookie, "Leave Room");

        Cookie joinerCookie = signUpAndGetCookie("mem6@test.com", "mem6");
        mockMvc.perform(post("/api/rooms/{roomId}/join", roomId)
                        .cookie(joinerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{roomId}/leave", roomId)
                        .cookie(joinerCookie))
                .andExpect(status().isOk());

        MvcResult membersResult = mockMvc.perform(get("/api/rooms/{roomId}/members", roomId)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        assertThat(members).hasSize(1);
    }

    @Test
    void leaveRoom_ownerCannotLeave() throws Exception {
        Cookie ownerCookie = signUpAndGetCookie("mem7@test.com", "mem7");
        String roomId = createRoom(ownerCookie, "Owner Leave Room");

        mockMvc.perform(post("/api/rooms/{roomId}/leave", roomId)
                        .cookie(ownerCookie))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getRoom_nonMember_returns403() throws Exception {
        Cookie ownerCookie = signUpAndGetCookie("mem8@test.com", "mem8");
        String roomId = createRoom(ownerCookie, "Forbidden Room");

        Cookie outsiderCookie = signUpAndGetCookie("mem9@test.com", "mem9");
        mockMvc.perform(get("/api/rooms/{roomId}", roomId)
                        .cookie(outsiderCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMembers_nonMember_returns403() throws Exception {
        Cookie ownerCookie = signUpAndGetCookie("mem10@test.com", "mem10");
        String roomId = createRoom(ownerCookie, "Forbidden Members Room");

        Cookie outsiderCookie = signUpAndGetCookie("mem11@test.com", "mem11");
        mockMvc.perform(get("/api/rooms/{roomId}/members", roomId)
                        .cookie(outsiderCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void joinRoom_roomNotFound_returns404() throws Exception {
        Cookie cookie = signUpAndGetCookie("mem12@test.com", "mem12");
        String fakeRoomId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/rooms/{roomId}/join", fakeRoomId)
                        .cookie(cookie))
                .andExpect(status().isNotFound());
    }
}
