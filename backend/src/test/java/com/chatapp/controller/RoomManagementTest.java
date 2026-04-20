package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomManagementTest extends BaseIntegrationTest {

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

    private String createRoom(UserInfo owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"original description"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void joinRoom(UserInfo user, String roomId) throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user.cookie))
                .andExpect(status().isOk());
    }

    private void promoteToAdmin(UserInfo owner, String roomId, Long userId) throws Exception {
        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + userId + "/role")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateRoom_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("rmgmt1a@test.com", "rmgmt1a");
        String roomId = createRoom(owner, "rmgmt-room-1");

        mockMvc.perform(put("/api/rooms/" + roomId)
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"new-name\",\"description\":\"new desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new-name"))
                .andExpect(jsonPath("$.description").value("new desc"));
    }

    @Test
    void updateRoom_asAdmin_succeeds() throws Exception {
        UserInfo owner = signUp("rmgmt2a@test.com", "rmgmt2a");
        UserInfo admin = signUp("rmgmt2b@test.com", "rmgmt2b");
        String roomId = createRoom(owner, "rmgmt-room-2");
        joinRoom(admin, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        mockMvc.perform(put("/api/rooms/" + roomId)
                        .cookie(admin.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"admin-rename\",\"description\":\"admin desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("admin-rename"));
    }

    @Test
    void updateRoom_asMember_fails() throws Exception {
        UserInfo owner = signUp("rmgmt3a@test.com", "rmgmt3a");
        UserInfo user2 = signUp("rmgmt3b@test.com", "rmgmt3b");
        String roomId = createRoom(owner, "rmgmt-room-3");
        joinRoom(user2, roomId);

        mockMvc.perform(put("/api/rooms/" + roomId)
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hacked-name\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRoom_duplicateName_fails() throws Exception {
        UserInfo owner = signUp("rmgmt4a@test.com", "rmgmt4a");
        createRoom(owner, "taken-name");
        String roomId = createRoom(owner, "rmgmt-room-4");

        mockMvc.perform(put("/api/rooms/" + roomId)
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"taken-name\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRoom_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("rdel1a@test.com", "rdel1a");
        UserInfo user2 = signUp("rdel1b@test.com", "rdel1b");
        String roomId = createRoom(owner, "rdel-room-1");
        joinRoom(user2, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRoom_asAdmin_fails() throws Exception {
        UserInfo owner = signUp("rdel2a@test.com", "rdel2a");
        UserInfo admin = signUp("rdel2b@test.com", "rdel2b");
        String roomId = createRoom(owner, "rdel-room-2");
        joinRoom(admin, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(admin.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRoom_asMember_fails() throws Exception {
        UserInfo owner = signUp("rdel3a@test.com", "rdel3a");
        UserInfo user2 = signUp("rdel3b@test.com", "rdel3b");
        String roomId = createRoom(owner, "rdel-room-3");
        joinRoom(user2, roomId);

        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(user2.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRoom_cascadesMessages() throws Exception {
        UserInfo owner = signUp("rdel4a@test.com", "rdel4a");
        String roomId = createRoom(owner, "rdel-room-4");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                            .cookie(owner.cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Message " + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(roomId)).doesNotExist());
    }

    @Test
    void deleteRoom_removesFromUserRoomList() throws Exception {
        UserInfo owner = signUp("rdel5a@test.com", "rdel5a");
        UserInfo user2 = signUp("rdel5b@test.com", "rdel5b");
        String roomId = createRoom(owner, "rdel-room-5");
        joinRoom(user2, roomId);

        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(roomId)).doesNotExist());
    }
}
