package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomBrowsingTest extends BaseIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static String unique() {
        return String.valueOf(COUNTER.incrementAndGet());
    }

    private Cookie signUpAndGetCookie(String suffix) throws Exception {
        String email = "rb" + suffix + "@test.com";
        String username = "rb" + suffix;
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

    private Cookie createRoom(Cookie cookie, String name) throws Exception {
        String body = """
                {
                    "name": "%s",
                    "description": "test room"
                }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return latestCookie(result, cookie);
    }

    @Test
    void getPublicRooms_returnsRooms() throws Exception {
        String id = unique();
        Cookie cookie = signUpAndGetCookie("br1_" + id);
        String roomName = "browse_room_" + id;
        cookie = createRoom(cookie, roomName);

        MvcResult result = mockMvc.perform(get("/api/rooms/public")
                        .param("search", roomName)
                        .param("page", "0")
                        .param("size", "20")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = page.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (JsonNode room : content) {
            if (roomName.equals(room.get("name").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void getPublicRooms_searchByName() throws Exception {
        String id = unique();
        Cookie cookie = signUpAndGetCookie("br2_" + id);
        cookie = createRoom(cookie, "srch_alpha_" + id);
        cookie = createRoom(cookie, "srch_beta_" + id);
        cookie = createRoom(cookie, "other_gamma_" + id);

        MvcResult result = mockMvc.perform(get("/api/rooms/public")
                        .param("search", "srch_")
                        .param("page", "0")
                        .param("size", "20")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = page.get("content");
        int count = 0;
        for (JsonNode room : content) {
            String name = room.get("name").asText();
            if (name.contains("srch_") && name.contains(id)) {
                count++;
            }
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void getPublicRooms_searchCaseInsensitive() throws Exception {
        String id = unique();
        Cookie cookie = signUpAndGetCookie("br3_" + id);
        String roomName = "CamelCase_" + id;
        cookie = createRoom(cookie, roomName);

        MvcResult result = mockMvc.perform(get("/api/rooms/public")
                        .param("search", "camelcase_" + id)
                        .param("page", "0")
                        .param("size", "20")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = page.get("content");
        boolean found = false;
        for (JsonNode room : content) {
            if (roomName.equals(room.get("name").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void getPublicRooms_pagination() throws Exception {
        String id = unique();
        Cookie cookie = signUpAndGetCookie("br4_" + id);
        for (int i = 1; i <= 5; i++) {
            cookie = createRoom(cookie, "paged_" + id + "_" + i);
        }

        MvcResult result = mockMvc.perform(get("/api/rooms/public")
                        .param("search", "paged_" + id)
                        .param("page", "0")
                        .param("size", "3")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("content").size()).isEqualTo(3);
        assertThat(page.has("totalPages")).isTrue();
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(5);
    }

    @Test
    void getPublicRooms_showsMemberCount() throws Exception {
        String id = unique();
        Cookie cookie = signUpAndGetCookie("br5_" + id);
        String roomName = "mcnt_" + id;
        cookie = createRoom(cookie, roomName);

        MvcResult result = mockMvc.perform(get("/api/rooms/public")
                        .param("search", roomName)
                        .param("page", "0")
                        .param("size", "20")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = page.get("content");
        boolean found = false;
        for (JsonNode room : content) {
            if (roomName.equals(room.get("name").asText())) {
                assertThat(room.get("memberCount").asInt()).isEqualTo(1);
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void getPublicRooms_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/rooms/public")
                        .param("search", "")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isForbidden());
    }
}
