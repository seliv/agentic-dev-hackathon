package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomInvitationTest extends BaseIntegrationTest {

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

    private String createPrivateRoom(Cookie cookie, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"Private room","type":"PRIVATE"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void inviteUser_toPrivateRoom_success() throws Exception {
        UserInfo owner = signUp("inv1a@test.com", "inv1a");
        UserInfo invitee = signUp("inv1b@test.com", "inv1b");
        String roomId = createPrivateRoom(owner.cookie, "invite-room-1");

        mockMvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(invitee.id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomName").value("invite-room-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void inviteUser_toPublicRoom_returnsConflict() throws Exception {
        UserInfo owner = signUp("inv2a@test.com", "inv2a");
        UserInfo invitee = signUp("inv2b@test.com", "inv2b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"public-invite-room","description":"Public"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(invitee.id)))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptInvitation_joinsRoom() throws Exception {
        UserInfo owner = signUp("inv3a@test.com", "inv3a");
        UserInfo invitee = signUp("inv3b@test.com", "inv3b");
        String roomId = createPrivateRoom(owner.cookie, "invite-room-3");

        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(invitee.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long invitationId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/invitations/" + invitationId + "/accept")
                        .cookie(invitee.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Invitee should now be a member
        mockMvc.perform(get("/api/rooms/" + roomId + "/members").cookie(invitee.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void declineInvitation_doesNotJoinRoom() throws Exception {
        UserInfo owner = signUp("inv4a@test.com", "inv4a");
        UserInfo invitee = signUp("inv4b@test.com", "inv4b");
        String roomId = createPrivateRoom(owner.cookie, "invite-room-4");

        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(invitee.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long invitationId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/invitations/" + invitationId + "/decline")
                        .cookie(invitee.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        // Invitee should not be a member
        mockMvc.perform(get("/api/rooms/" + roomId).cookie(invitee.cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPendingInvitations_returnsUserInvitations() throws Exception {
        UserInfo owner = signUp("inv5a@test.com", "inv5a");
        UserInfo invitee = signUp("inv5b@test.com", "inv5b");
        String roomId = createPrivateRoom(owner.cookie, "invite-room-5");

        mockMvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(invitee.id)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/invitations").cookie(invitee.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roomName").value("invite-room-5"));
    }

    @Test
    void inviteUser_alreadyMember_returnsConflict() throws Exception {
        UserInfo owner = signUp("inv6a@test.com", "inv6a");
        String roomId = createPrivateRoom(owner.cookie, "invite-room-6");

        // Try to invite the owner (already a member)
        mockMvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(owner.id)))
                .andExpect(status().isConflict());
    }
}
