package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessageAttachmentTest extends BaseIntegrationTest {

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

    @Test
    void uploadFileWithMessage_returnsAttachment() throws Exception {
        UserInfo user = signUp("att1@test.com", "att1");
        String roomId = createRoomAndJoin(user, "att-room-1");

        MockMultipartFile file = new MockMultipartFile(
                "files", "hello.txt", "text/plain", "hello world".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "here is a file")
                        .cookie(user.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        mockMvc.perform(get("/").contentType(MediaType.APPLICATION_JSON));
        String fileName = objectMapper.readTree(body)
                .get("attachments").get(0).get("originalFileName").asText();
        org.assertj.core.api.Assertions.assertThat(fileName).isEqualTo("hello.txt");
    }

    @Test
    void uploadImage_hasThumbnailUrl() throws Exception {
        UserInfo user = signUp("att2@test.com", "att2");
        String roomId = createRoomAndJoin(user, "att-room-2");

        byte[] minimalPng = new byte[]{
                (byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,
                0x00,0x00,0x00,0x0d,0x49,0x48,0x44,0x52,
                0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,
                0x08,0x02,0x00,0x00,0x00,(byte)0x90,0x77,0x53,(byte)0xde,
                0x00,0x00,0x00,0x0c,0x49,0x44,0x41,0x54,
                0x08,(byte)0xd7,0x63,(byte)0xf8,(byte)0xcf,(byte)0xc0,0x00,0x00,0x00,0x02,0x00,0x01,
                (byte)0xe2,0x21,(byte)0xbc,0x33,0x00,0x00,0x00,0x00,
                0x49,0x45,0x4e,0x44,(byte)0xae,0x42,0x60,(byte)0x82
        };

        MockMultipartFile file = new MockMultipartFile(
                "files", "image.png", "image/png", minimalPng);

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "image here")
                        .cookie(user.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String thumbnailUrl = objectMapper.readTree(body)
                .get("attachments").get(0).get("thumbnailUrl").asText();
        org.assertj.core.api.Assertions.assertThat(thumbnailUrl).isNotBlank();

        String attachmentId = objectMapper.readTree(body)
                .get("attachments").get(0).get("id").asText();
        mockMvc.perform(get("/api/attachments/" + attachmentId + "/thumbnail")
                        .cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    void downloadFile_asMember_succeeds() throws Exception {
        UserInfo user1 = signUp("att3a@test.com", "att3a");
        UserInfo user2 = signUp("att3b@test.com", "att3b");
        String roomId = createRoomAndJoin(user1, "att-room-3", user2);

        MockMultipartFile file = new MockMultipartFile(
                "files", "doc.txt", "text/plain", "document content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "shared doc")
                        .cookie(user1.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        mockMvc.perform(get("/api/attachments/" + attachmentId)
                        .cookie(user2.cookie()))
                .andExpect(status().isOk());
    }

    @Test
    void downloadFile_asNonMember_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("att4a@test.com", "att4a");
        UserInfo user2 = signUp("att4b@test.com", "att4b");
        String roomId = createRoomAndJoin(user1, "att-room-4");

        MockMultipartFile file = new MockMultipartFile(
                "files", "secret.txt", "text/plain", "secret content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "secret")
                        .cookie(user1.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        mockMvc.perform(get("/api/attachments/" + attachmentId)
                        .cookie(user2.cookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadMultipleFiles_returnsAll() throws Exception {
        UserInfo user = signUp("att5@test.com", "att5");
        String roomId = createRoomAndJoin(user, "att-room-5");

        MockMultipartFile file1 = new MockMultipartFile("files", "a.txt", "text/plain", "aaa".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "b.txt", "text/plain", "bbb".getBytes());
        MockMultipartFile file3 = new MockMultipartFile("files", "c.txt", "text/plain", "ccc".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file1)
                        .file(file2)
                        .file(file3)
                        .param("content", "three files")
                        .cookie(user.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        int count = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").size();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(3);
    }

    @Test
    void uploadWithoutContentOrFiles_returnsBadRequest() throws Exception {
        UserInfo user = signUp("att6@test.com", "att6");
        String roomId = createRoomAndJoin(user, "att-room-6");

        mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .cookie(user.cookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadDeletedMessageAttachment_returnsNotFound() throws Exception {
        UserInfo user = signUp("att7@test.com", "att7");
        String roomId = createRoomAndJoin(user, "att-room-7");

        MockMultipartFile file = new MockMultipartFile("files", "bye.txt", "text/plain", "bye".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "will be deleted")
                        .cookie(user.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String msgId = objectMapper.readTree(body).get("id").asText();
        String attachmentId = objectMapper.readTree(body).get("attachments").get(0).get("id").asText();

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attachments/" + attachmentId)
                        .cookie(user.cookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void thumbnailForNonImage_returnsNotFound() throws Exception {
        UserInfo user = signUp("att8@test.com", "att8");
        String roomId = createRoomAndJoin(user, "att-room-8");

        MockMultipartFile file = new MockMultipartFile("files", "data.csv", "text/csv", "a,b,c".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "csv file")
                        .cookie(user.cookie()))
                .andExpect(status().isCreated())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        mockMvc.perform(get("/api/attachments/" + attachmentId + "/thumbnail")
                        .cookie(user.cookie()))
                .andExpect(status().isNotFound());
    }
}
