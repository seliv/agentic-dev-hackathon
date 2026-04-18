package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomCreationTest extends BaseIntegrationTest {

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

    private Cookie latestCookie(MvcResult result, Cookie previous) {
        Cookie fresh = result.getResponse().getCookie("SESSION");
        return fresh != null ? fresh : previous;
    }

    @Test
    void createRoom_validRequest_returns201() throws Exception {
        Cookie cookie = signUpAndGetCookie("room1@test.com", "room1");

        String body = """
                {
                    "name": "Room One",
                    "description": "First room"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("name").asText()).isEqualTo("Room One");
        assertThat(json.get("description").asText()).isEqualTo("First room");
        assertThat(json.get("type").asText()).isEqualTo("PUBLIC");
        assertThat(json.get("id").asText()).isNotBlank();
        assertThat(json.get("memberCount").asInt()).isEqualTo(1);
        assertThat(json.get("ownerUsername").asText()).isEqualTo("room1");
    }

    @Test
    void createRoom_creatorBecomesOwnerMember() throws Exception {
        Cookie cookie = signUpAndGetCookie("room2@test.com", "room2");

        String body = """
                {
                    "name": "Room Two",
                    "description": "Owner test"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        cookie = latestCookie(createResult, cookie);
        String roomId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult membersResult = mockMvc.perform(get("/api/rooms/{roomId}/members", roomId)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("role").asText()).isEqualTo("OWNER");
    }

    @Test
    void createRoom_appearsInMyRooms() throws Exception {
        Cookie cookie = signUpAndGetCookie("room3@test.com", "room3");

        String body = """
                {
                    "name": "Room Three",
                    "description": "My rooms test"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        cookie = latestCookie(createResult, cookie);

        MvcResult listResult = mockMvc.perform(get("/api/rooms")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rooms = objectMapper.readTree(listResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode room : rooms) {
            if ("Room Three".equals(room.get("name").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void createRoom_duplicateName_returns409() throws Exception {
        Cookie cookie = signUpAndGetCookie("room4@test.com", "room4");

        String body = """
                {
                    "name": "Duplicate Room",
                    "description": "First"
                }
                """;

        MvcResult firstResult = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        cookie = latestCookie(firstResult, cookie);

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Room with this name already exists"));
    }

    @Test
    void createRoom_missingName_returns400() throws Exception {
        Cookie cookie = signUpAndGetCookie("room5@test.com", "room5");

        String body = """
                {
                    "description": "No name"
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoom_notAuthenticated_returns403() throws Exception {
        String body = """
                {
                    "name": "Unauth Room",
                    "description": "Should fail"
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
