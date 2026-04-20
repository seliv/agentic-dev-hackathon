package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModerationTest extends BaseIntegrationTest {

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
                            {"name":"%s","description":"test room"}
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
    void promoteToAdmin_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("mod1a@test.com", "mod1a");
        UserInfo user2 = signUp("mod1b@test.com", "mod1b");
        String roomId = createRoom(owner, "mod-room-1");
        joinRoom(user2, roomId);

        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + user2.id + "/role")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void demoteFromAdmin_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("mod2a@test.com", "mod2a");
        UserInfo user2 = signUp("mod2b@test.com", "mod2b");
        String roomId = createRoom(owner, "mod-room-2");
        joinRoom(user2, roomId);
        promoteToAdmin(owner, roomId, user2.id);

        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + user2.id + "/role")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void promoteToAdmin_asNonOwner_returnsForbidden() throws Exception {
        UserInfo owner = signUp("mod3a@test.com", "mod3a");
        UserInfo admin = signUp("mod3b@test.com", "mod3b");
        UserInfo user3 = signUp("mod3c@test.com", "mod3c");
        String roomId = createRoom(owner, "mod-room-3");
        joinRoom(admin, roomId);
        joinRoom(user3, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + user3.id + "/role")
                        .cookie(admin.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changeOwnerRole_fails() throws Exception {
        UserInfo owner = signUp("mod4a@test.com", "mod4a");
        String roomId = createRoom(owner, "mod-room-4");

        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + owner.id + "/role")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void banUser_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("ban1a@test.com", "ban1a");
        UserInfo user2 = signUp("ban1b@test.com", "ban1b");
        String roomId = createRoom(owner, "ban-room-1");
        joinRoom(user2, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user2.id + ",\"reason\":\"spam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(user2.id))
                .andExpect(jsonPath("$.reason").value("spam"));

        mockMvc.perform(get("/api/rooms/" + roomId + "/members").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void banUser_asAdmin_succeeds() throws Exception {
        UserInfo owner = signUp("ban2a@test.com", "ban2a");
        UserInfo admin = signUp("ban2b@test.com", "ban2b");
        UserInfo user3 = signUp("ban2c@test.com", "ban2c");
        String roomId = createRoom(owner, "ban-room-2");
        joinRoom(admin, roomId);
        joinRoom(user3, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(admin.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user3.id + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void banUser_asMember_fails() throws Exception {
        UserInfo owner = signUp("ban3a@test.com", "ban3a");
        UserInfo user2 = signUp("ban3b@test.com", "ban3b");
        UserInfo user3 = signUp("ban3c@test.com", "ban3c");
        String roomId = createRoom(owner, "ban-room-3");
        joinRoom(user2, roomId);
        joinRoom(user3, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user3.id + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void banOwner_fails() throws Exception {
        UserInfo owner = signUp("ban4a@test.com", "ban4a");
        UserInfo admin = signUp("ban4b@test.com", "ban4b");
        String roomId = createRoom(owner, "ban-room-4");
        joinRoom(admin, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(admin.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + owner.id + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void bannedUser_cannotRejoin() throws Exception {
        UserInfo owner = signUp("ban5a@test.com", "ban5a");
        UserInfo user2 = signUp("ban5b@test.com", "ban5b");
        String roomId = createRoom(owner, "ban-room-5");
        joinRoom(user2, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user2.id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user2.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void unbanUser_thenRejoin_succeeds() throws Exception {
        UserInfo owner = signUp("ban6a@test.com", "ban6a");
        UserInfo user2 = signUp("ban6b@test.com", "ban6b");
        String roomId = createRoom(owner, "ban-room-6");
        joinRoom(user2, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user2.id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/rooms/" + roomId + "/bans/" + user2.id)
                        .cookie(owner.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user2.cookie))
                .andExpect(status().isOk());
    }

    @Test
    void listBannedUsers_returnsAll() throws Exception {
        UserInfo owner = signUp("ban7a@test.com", "ban7a");
        UserInfo user2 = signUp("ban7b@test.com", "ban7b");
        UserInfo user3 = signUp("ban7c@test.com", "ban7c");
        String roomId = createRoom(owner, "ban-room-7");
        joinRoom(user2, roomId);
        joinRoom(user3, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user2.id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user3.id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms/" + roomId + "/bans").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void kickMember_asOwner_bansUser() throws Exception {
        UserInfo owner = signUp("kick1a@test.com", "kick1a");
        UserInfo user2 = signUp("kick1b@test.com", "kick1b");
        String roomId = createRoom(owner, "kick-room-1");
        joinRoom(user2, roomId);

        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/" + user2.id)
                        .cookie(owner.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user2.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanBanOtherAdmin() throws Exception {
        UserInfo owner = signUp("kick2a@test.com", "kick2a");
        UserInfo admin1 = signUp("kick2b@test.com", "kick2b");
        UserInfo admin2 = signUp("kick2c@test.com", "kick2c");
        String roomId = createRoom(owner, "kick-room-2");
        joinRoom(admin1, roomId);
        joinRoom(admin2, roomId);
        promoteToAdmin(owner, roomId, admin1.id);
        promoteToAdmin(owner, roomId, admin2.id);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(admin1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + admin2.id + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanDeleteOtherUsersMessage() throws Exception {
        UserInfo owner = signUp("adel1a@test.com", "adel1a");
        UserInfo admin = signUp("adel1b@test.com", "adel1b");
        UserInfo user3 = signUp("adel1c@test.com", "adel1c");
        String roomId = createRoom(owner, "adel-room-1");
        joinRoom(admin, roomId);
        joinRoom(user3, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        MvcResult msgResult = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user3.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello from user3\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String msgId = objectMapper.readTree(msgResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(admin.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true));
    }

    @Test
    void memberCannotDeleteOtherUsersMessage() throws Exception {
        UserInfo owner = signUp("adel2a@test.com", "adel2a");
        UserInfo user2 = signUp("adel2b@test.com", "adel2b");
        UserInfo user3 = signUp("adel2c@test.com", "adel2c");
        String roomId = createRoom(owner, "adel-room-2");
        joinRoom(user2, roomId);
        joinRoom(user3, roomId);

        MvcResult msgResult = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user3.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String msgId = objectMapper.readTree(msgResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user2.cookie))
                .andExpect(status().isConflict());
    }
}
