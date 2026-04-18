package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomMessagesTest extends BaseIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private UserRepository userRepository;

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
    void getMessages_emptyRoom_returnsEmptyList() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg1@test.com", "msg1");
        String roomId = createRoom(cookie, "Empty Msg Room");

        MvcResult result = mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode messages = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(messages).isEmpty();
    }

    @Test
    void getMessages_returnsMessagesInRoom() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg2@test.com", "msg2");
        String roomId = createRoom(cookie, "Messages Room");

        ChatRoom room = chatRoomRepository.findById(UUID.fromString(roomId)).orElseThrow();
        User sender = userRepository.findByEmail("msg2@test.com").orElseThrow();

        for (int i = 1; i <= 3; i++) {
            Message msg = new Message();
            msg.setRoom(room);
            msg.setSender(sender);
            msg.setContent("Message " + i);
            messageRepository.save(msg);
        }

        MvcResult result = mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode messages = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(messages).hasSize(3);

        for (JsonNode msg : messages) {
            assertThat(msg.get("senderUsername").asText()).isEqualTo("msg2");
            assertThat(msg.get("roomId").asText()).isEqualTo(roomId);
            assertThat(msg.get("content").asText()).startsWith("Message ");
        }
    }

    @Test
    void getMessages_cursorPagination() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg3@test.com", "msg3");
        String roomId = createRoom(cookie, "Pagination Room");

        ChatRoom room = chatRoomRepository.findById(UUID.fromString(roomId)).orElseThrow();
        User sender = userRepository.findByEmail("msg3@test.com").orElseThrow();

        for (int i = 1; i <= 5; i++) {
            Message msg = new Message();
            msg.setRoom(room);
            msg.setSender(sender);
            msg.setContent("Paged " + i);
            messageRepository.save(msg);
            Thread.sleep(10);
        }

        MvcResult firstPage = mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .param("limit", "3")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstMessages = objectMapper.readTree(firstPage.getResponse().getContentAsString());
        assertThat(firstMessages).hasSize(3);

        String oldestTimestamp = firstMessages.get(2).get("createdAt").asText();

        MvcResult secondPage = mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .param("before", oldestTimestamp)
                        .param("limit", "3")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondMessages = objectMapper.readTree(secondPage.getResponse().getContentAsString());
        assertThat(secondMessages).hasSize(2);
    }

    @Test
    void getMessages_nonMember_returns403() throws Exception {
        Cookie ownerCookie = signUpAndGetCookie("msg4@test.com", "msg4owner");
        String roomId = createRoom(ownerCookie, "Private Msg Room");

        Cookie otherCookie = signUpAndGetCookie("msg4other@test.com", "msg4other");

        mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .cookie(otherCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMessages_limitCapped() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg5@test.com", "msg5");
        String roomId = createRoom(cookie, "Limit Cap Room");

        mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .param("limit", "200")
                        .cookie(cookie))
                .andExpect(status().isOk());
    }
}
