package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessageEditDeleteTest extends BaseIntegrationTest {

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
                        .cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"test room"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        for (UserInfo other : others) {
            mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(other.cookie()))
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

    private String sendMsgWithReply(Cookie cookie, String roomId, String content, String replyToId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"%s","replyToId":"%s"}
                        """.formatted(content, replyToId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void editMessage_ownMessage_succeeds() throws Exception {
        UserInfo user = signUp("ed1@test.com", "ed1");
        String roomId = createRoomAndJoin(user, "ed-room-1");
        String msgId = sendMsg(user.cookie(), roomId, "original content");

        MvcResult result = mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"edited content"}
                        """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("content").asText()).isEqualTo("edited content");
        assertThat(objectMapper.readTree(body).get("editedAt").asText()).isNotBlank();
    }

    @Test
    void editMessage_otherUser_returnsConflict() throws Exception {
        UserInfo user1 = signUp("ed2a@test.com", "ed2a");
        UserInfo user2 = signUp("ed2b@test.com", "ed2b");
        String roomId = createRoomAndJoin(user1, "ed-room-2", user2);
        String msgId = sendMsg(user1.cookie(), roomId, "user1 message");

        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user2.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"hacked"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void editMessage_deletedMessage_returnsConflict() throws Exception {
        UserInfo user = signUp("ed3@test.com", "ed3");
        String roomId = createRoomAndJoin(user, "ed-room-3");
        String msgId = sendMsg(user.cookie(), roomId, "to be deleted");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"edit after delete"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteMessage_ownMessage_succeeds() throws Exception {
        UserInfo user = signUp("ed4@test.com", "ed4");
        String roomId = createRoomAndJoin(user, "ed-room-4");
        String msgId = sendMsg(user.cookie(), roomId, "delete me");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user.cookie()))
                .andExpect(status().isOk());

        MvcResult history = mockMvc.perform(get("/api/rooms/" + roomId + "/messages")
                        .cookie(user.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        var messages = objectMapper.readTree(history.getResponse().getContentAsString());
        var msg = messages.get(0);
        assertThat(msg.get("deleted").asBoolean()).isTrue();
        assertThat(msg.get("content").isNull()).isTrue();
    }

    @Test
    void deleteMessage_roomOwner_canDeleteOthersMessages() throws Exception {
        UserInfo owner = signUp("ed5a@test.com", "ed5a");
        UserInfo member = signUp("ed5b@test.com", "ed5b");
        String roomId = createRoomAndJoin(owner, "ed-room-5", member);
        String msgId = sendMsg(member.cookie(), roomId, "member message");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMessage_nonOwnerNonSender_returnsConflict() throws Exception {
        UserInfo owner = signUp("ed6a@test.com", "ed6a");
        UserInfo user2 = signUp("ed6b@test.com", "ed6b");
        UserInfo user3 = signUp("ed6c@test.com", "ed6c");
        String roomId = createRoomAndJoin(owner, "ed-room-6", user2, user3);
        String msgId = sendMsg(user2.cookie(), roomId, "user2 message");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user3.cookie()))
                .andExpect(status().isConflict());
    }

    @Test
    void replyToMessage_showsReplyPreview() throws Exception {
        UserInfo user = signUp("ed7@test.com", "ed7");
        String roomId = createRoomAndJoin(user, "ed-room-7");
        String originalId = sendMsg(user.cookie(), roomId, "original message");

        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"reply here","replyToId":"%s"}
                        """.formatted(originalId)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("replyToId").asText()).isEqualTo(originalId);
        assertThat(objectMapper.readTree(body).get("replyToPreview").get("content").asText())
                .isEqualTo("original message");
    }

    @Test
    void replyToDeletedMessage_showsDeletedPreview() throws Exception {
        UserInfo user = signUp("ed8@test.com", "ed8");
        String roomId = createRoomAndJoin(user, "ed-room-8");
        String originalId = sendMsg(user.cookie(), roomId, "will be deleted");
        sendMsgWithReply(user.cookie(), roomId, "reply to it", originalId);

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + originalId)
                        .cookie(user.cookie()))
                .andExpect(status().isOk());

        MvcResult history = mockMvc.perform(get("/api/rooms/" + roomId + "/messages")
                        .cookie(user.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        var messages = objectMapper.readTree(history.getResponse().getContentAsString());
        // history is ordered newest first, so first item is the reply
        var reply = messages.get(0);
        assertThat(reply.get("replyToPreview").get("deleted").asBoolean()).isTrue();
    }

    @Test
    void replyToMessageInDifferentRoom_returnsBadRequest() throws Exception {
        UserInfo user = signUp("ed9@test.com", "ed9");
        String room1 = createRoomAndJoin(user, "ed-room-9a");
        String room2 = createRoomAndJoin(user, "ed-room-9b");
        String msgInRoom1 = sendMsg(user.cookie(), room1, "room1 message");

        mockMvc.perform(post("/api/rooms/" + room2 + "/messages")
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"cross-room reply","replyToId":"%s"}
                        """.formatted(msgInRoom1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageHistory_showsEditedAndDeletedStatus() throws Exception {
        UserInfo user = signUp("ed10@test.com", "ed10");
        String roomId = createRoomAndJoin(user, "ed-room-10");

        String msg1 = sendMsg(user.cookie(), roomId, "message one");
        String msg2 = sendMsg(user.cookie(), roomId, "message two");
        String msg3 = sendMsg(user.cookie(), roomId, "message three");

        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msg1)
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"message one edited"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msg2)
                        .cookie(user.cookie()))
                .andExpect(status().isOk());

        MvcResult history = mockMvc.perform(get("/api/rooms/" + roomId + "/messages")
                        .cookie(user.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        var messages = objectMapper.readTree(history.getResponse().getContentAsString());
        assertThat(messages).hasSize(3);

        // history ordered newest first: msg3, msg2, msg1
        var msg3Node = messages.get(0);
        var msg2Node = messages.get(1);
        var msg1Node = messages.get(2);

        assertThat(msg3Node.get("content").asText()).isEqualTo("message three");
        assertThat(msg3Node.get("deleted").asBoolean()).isFalse();

        assertThat(msg2Node.get("deleted").asBoolean()).isTrue();
        assertThat(msg2Node.get("content").isNull()).isTrue();

        assertThat(msg1Node.get("content").asText()).isEqualTo("message one edited");
        assertThat(msg1Node.get("editedAt").asText()).isNotBlank();
    }
}
