package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UnreadTest extends BaseIntegrationTest {

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

    private String createRoomAndJoin(UserInfo owner, String name, UserInfo... others) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"test room"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        for (UserInfo other : others) {
            mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(other.cookie))
                    .andExpect(status().isOk());
        }
        return roomId;
    }

    private String sendMsg(Cookie cookie, String roomId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"%s"}
                        """.formatted(content)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void getUnreadCounts_noMessages_returnsEmpty() throws Exception {
        UserInfo user1 = signUp("unr1@test.com", "unr1");
        createRoomAndJoin(user1, "empty-room-1");

        mockMvc.perform(get("/api/rooms/unread").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getUnreadCounts_withUnreadMessages() throws Exception {
        UserInfo user1 = signUp("unr2a@test.com", "unr2a");
        UserInfo user2 = signUp("unr2b@test.com", "unr2b");
        String roomId = createRoomAndJoin(user1, "unread-room-2", user2);

        sendMsg(user1.cookie, roomId, "Hello 1");
        sendMsg(user1.cookie, roomId, "Hello 2");
        sendMsg(user1.cookie, roomId, "Hello 3");

        mockMvc.perform(get("/api/rooms/unread").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(roomId))
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    void markAsRead_clearsUnreadCount() throws Exception {
        UserInfo user1 = signUp("unr3a@test.com", "unr3a");
        UserInfo user2 = signUp("unr3b@test.com", "unr3b");
        String roomId = createRoomAndJoin(user1, "unread-room-3", user2);

        sendMsg(user1.cookie, roomId, "Hello 1");
        sendMsg(user1.cookie, roomId, "Hello 2");
        String msgId3 = sendMsg(user1.cookie, roomId, "Hello 3");

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId3)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/unread").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void markAsRead_partialRead_showsRemaining() throws Exception {
        UserInfo user1 = signUp("unr4a@test.com", "unr4a");
        UserInfo user2 = signUp("unr4b@test.com", "unr4b");
        String roomId = createRoomAndJoin(user1, "unread-room-4", user2);

        String msgId1 = sendMsg(user1.cookie, roomId, "Hello 1");
        sendMsg(user1.cookie, roomId, "Hello 2");
        sendMsg(user1.cookie, roomId, "Hello 3");

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId1)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/unread").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(2));
    }

    @Test
    void markAsRead_nonMember_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("unr5a@test.com", "unr5a");
        UserInfo user2 = signUp("unr5b@test.com", "unr5b");
        String roomId = createRoomAndJoin(user1, "unread-room-5");

        String msgId = sendMsg(user1.cookie, roomId, "Hello");

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsRead_invalidMessageId_returnsBadRequest() throws Exception {
        UserInfo user1 = signUp("unr6@test.com", "unr6");
        String roomId = createRoomAndJoin(user1, "unread-room-6");

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"00000000-0000-0000-0000-000000000000"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnreadCounts_multipleRooms() throws Exception {
        UserInfo user1 = signUp("unr7a@test.com", "unr7a");
        UserInfo user2 = signUp("unr7b@test.com", "unr7b");
        String room1 = createRoomAndJoin(user1, "unread-room-7a", user2);
        String room2 = createRoomAndJoin(user1, "unread-room-7b", user2);

        sendMsg(user1.cookie, room1, "Room1 msg1");
        sendMsg(user1.cookie, room1, "Room1 msg2");
        sendMsg(user1.cookie, room2, "Room2 msg1");

        mockMvc.perform(get("/api/rooms/unread").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void markAsRead_upserts_onSecondCall() throws Exception {
        UserInfo user1 = signUp("unr8a@test.com", "unr8a");
        UserInfo user2 = signUp("unr8b@test.com", "unr8b");
        String roomId = createRoomAndJoin(user1, "unread-room-8", user2);

        String msgId1 = sendMsg(user1.cookie, roomId, "Hello 1");
        String msgId2 = sendMsg(user1.cookie, roomId, "Hello 2");

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId2)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/unread").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
