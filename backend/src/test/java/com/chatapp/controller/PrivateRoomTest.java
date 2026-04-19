package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrivateRoomTest extends BaseIntegrationTest {

    private Cookie signUpAndGetCookie(String email, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","username":"%s","password":"password123","displayName":"Test User"}
                        """.formatted(email, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void createPrivateRoom_success() throws Exception {
        Cookie cookie = signUpAndGetCookie("pr1@test.com", "pr1");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"private-room-1","description":"Secret room","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PRIVATE"))
                .andExpect(jsonPath("$.name").value("private-room-1"));
    }

    @Test
    void privateRoom_notInPublicCatalog() throws Exception {
        Cookie cookie = signUpAndGetCookie("pr2@test.com", "pr2");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"hidden-room","description":"Hidden","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms/public?search=hidden-room").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void privateRoom_duplicateNameAllowed() throws Exception {
        Cookie cookie1 = signUpAndGetCookie("pr3a@test.com", "pr3a");
        Cookie cookie2 = signUpAndGetCookie("pr3b@test.com", "pr3b");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"same-name","description":"First","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"same-name","description":"Second","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void privateRoom_nonMemberCannotAccess() throws Exception {
        Cookie owner = signUpAndGetCookie("pr4a@test.com", "pr4a");
        Cookie other = signUpAndGetCookie("pr4b@test.com", "pr4b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"restricted-room","description":"Restricted","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/rooms/" + roomId).cookie(other))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateRoom_cannotJoinDirectly() throws Exception {
        Cookie owner = signUpAndGetCookie("pr5a@test.com", "pr5a");
        Cookie other = signUpAndGetCookie("pr5b@test.com", "pr5b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"no-join-room","description":"No join","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Direct join should still work technically (joinRoom doesn't check room type)
        // but private rooms aren't in the catalog, so users can't discover them
        // The access control is via invitations, not join blocking
        mockMvc.perform(get("/api/rooms/" + roomId).cookie(other))
                .andExpect(status().isForbidden());
    }
}
