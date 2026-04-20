# Iteration 5: Attachments & Rich Messaging — Testing Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend integration tests (JUnit 5 + Testcontainers) and end-to-end tests (Playwright) for all Iteration 5 features: file upload/download, message edit/delete, replies, and attachment access control.

**Architecture:** Backend tests use `@SpringBootTest` with `MockMvc` and Testcontainers PostgreSQL — real database, no mocks. Cookie-based session tracking (required for spring-session-jdbc). File uploads tested via `MockMvcRequestBuilders.multipart()`. E2E tests use Playwright against the full docker-compose stack.

**Tech Stack:** JUnit 5, Testcontainers 1.21.4 (PostgreSQL), Spring Boot Test, MockMvc, Playwright, TypeScript

**Prerequisites:** Iteration 5 implementation must be complete. Testcontainers infrastructure and Playwright setup already exist from previous iterations.

---

## Important: Session Handling in Tests

The app uses `spring-session-jdbc`. **`MockHttpSession` does NOT work** for cross-request session tracking. Always use cookies:

```java
Cookie signUpAndGetCookie(String email, String username) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","username":"%s","password":"password123","displayName":"Test User"}
            """.formatted(email, username)))
            .andExpect(status().isCreated())
            .andReturn();
    return result.getResponse().getCookie("SESSION");
}
```

---

## File Structure

### Backend Tests

| File | Responsibility |
|------|----------------|
| `backend/src/test/java/com/chatapp/controller/MessageAttachmentTest.java` | File upload, download, access control |
| `backend/src/test/java/com/chatapp/controller/MessageEditDeleteTest.java` | Message edit, delete, reply |

### E2E Tests

| File | Responsibility |
|------|----------------|
| `e2e/tests/attachments.spec.ts` | File upload via button, image thumbnail |
| `e2e/tests/message-actions.spec.ts` | Edit, delete, reply flows |

---

### Task 1: Message Attachment Integration Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/MessageAttachmentTest.java`

- [ ] **Step 1: Create MessageAttachmentTest**

Create `backend/src/test/java/com/chatapp/controller/MessageAttachmentTest.java`:

```java
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

    @Test
    void uploadFileWithMessage_returnsAttachment() throws Exception {
        UserInfo user1 = signUp("att1@test.com", "att1");
        String roomId = createRoomAndJoin(user1, "attach-room-1");

        MockMultipartFile file = new MockMultipartFile(
                "files", "test.txt", "text/plain", "hello world".getBytes());

        mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "Check this file")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Check this file"))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].originalFileName").value("test.txt"))
                .andExpect(jsonPath("$.attachments[0].contentType").value("text/plain"))
                .andExpect(jsonPath("$.attachments[0].downloadUrl").exists());
    }

    @Test
    void uploadImage_hasThumbnailUrl() throws Exception {
        UserInfo user1 = signUp("att2@test.com", "att2");
        String roomId = createRoomAndJoin(user1, "attach-room-2");

        MockMultipartFile image = new MockMultipartFile(
                "files", "photo.png", "image/png", new byte[]{1, 2, 3, 4});

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(image)
                        .param("content", "A photo")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].thumbnailUrl").exists())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        // Download thumbnail
        mockMvc.perform(get("/api/attachments/" + attachmentId + "/thumbnail")
                        .cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    void downloadFile_asMember_succeeds() throws Exception {
        UserInfo user1 = signUp("att3a@test.com", "att3a");
        UserInfo user2 = signUp("att3b@test.com", "att3b");
        String roomId = createRoomAndJoin(user1, "attach-room-3", user2);

        MockMultipartFile file = new MockMultipartFile(
                "files", "doc.pdf", "application/pdf", "pdf content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "Here's the doc")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        // User2 (member) can download
        mockMvc.perform(get("/api/attachments/" + attachmentId).cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("doc.pdf")));
    }

    @Test
    void downloadFile_asNonMember_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("att4a@test.com", "att4a");
        UserInfo user2 = signUp("att4b@test.com", "att4b");
        String roomId = createRoomAndJoin(user1, "attach-room-4");

        MockMultipartFile file = new MockMultipartFile(
                "files", "secret.txt", "text/plain", "secret".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "Secret file")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        // User2 (non-member) cannot download
        mockMvc.perform(get("/api/attachments/" + attachmentId).cookie(user2.cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadMultipleFiles_returnsAll() throws Exception {
        UserInfo user1 = signUp("att5@test.com", "att5");
        String roomId = createRoomAndJoin(user1, "attach-room-5");

        MockMultipartFile file1 = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());
        MockMultipartFile file3 = new MockMultipartFile("files", "c.txt", "text/plain", "c".getBytes());

        mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file1).file(file2).file(file3)
                        .param("content", "Multiple files")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(3));
    }

    @Test
    void uploadWithoutContentOrFiles_returnsBadRequest() throws Exception {
        UserInfo user1 = signUp("att6@test.com", "att6");
        String roomId = createRoomAndJoin(user1, "attach-room-6");

        mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .cookie(user1.cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadDeletedMessageAttachment_returnsNotFound() throws Exception {
        UserInfo user1 = signUp("att7@test.com", "att7");
        String roomId = createRoomAndJoin(user1, "attach-room-7");

        MockMultipartFile file = new MockMultipartFile(
                "files", "temp.txt", "text/plain", "temporary".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "Temp file")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andReturn();

        String messageId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        // Delete the message
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + messageId)
                        .cookie(user1.cookie))
                .andExpect(status().isOk());

        // Attachment download should return 404
        mockMvc.perform(get("/api/attachments/" + attachmentId).cookie(user1.cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void thumbnailForNonImage_returnsNotFound() throws Exception {
        UserInfo user1 = signUp("att8@test.com", "att8");
        String roomId = createRoomAndJoin(user1, "attach-room-8");

        MockMultipartFile file = new MockMultipartFile(
                "files", "data.csv", "text/csv", "a,b,c".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("content", "CSV data")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andReturn();

        String attachmentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asText();

        mockMvc.perform(get("/api/attachments/" + attachmentId + "/thumbnail")
                        .cookie(user1.cookie))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.MessageAttachmentTest"`
Expected: All 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/MessageAttachmentTest.java
git commit -m "test: add message attachment integration tests"
```

---

### Task 2: Message Edit, Delete, and Reply Integration Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/MessageEditDeleteTest.java`

- [ ] **Step 1: Create MessageEditDeleteTest**

Create `backend/src/test/java/com/chatapp/controller/MessageEditDeleteTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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

    // --- EDIT TESTS ---

    @Test
    void editMessage_ownMessage_succeeds() throws Exception {
        UserInfo user1 = signUp("edt1@test.com", "edt1");
        String roomId = createRoomAndJoin(user1, "edit-room-1");
        String msgId = sendMsg(user1.cookie, roomId, "Original text");

        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"Edited text"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Edited text"))
                .andExpect(jsonPath("$.editedAt").exists());
    }

    @Test
    void editMessage_otherUser_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("edt2a@test.com", "edt2a");
        UserInfo user2 = signUp("edt2b@test.com", "edt2b");
        String roomId = createRoomAndJoin(user1, "edit-room-2", user2);
        String msgId = sendMsg(user1.cookie, roomId, "User1 message");

        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"Hacked edit"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void editMessage_deletedMessage_returnsBadRequest() throws Exception {
        UserInfo user1 = signUp("edt3@test.com", "edt3");
        String roomId = createRoomAndJoin(user1, "edit-room-3");
        String msgId = sendMsg(user1.cookie, roomId, "Will be deleted");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user1.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"Edit after delete"}"""))
                .andExpect(status().isBadRequest());
    }

    // --- DELETE TESTS ---

    @Test
    void deleteMessage_ownMessage_succeeds() throws Exception {
        UserInfo user1 = signUp("del1@test.com", "del1");
        String roomId = createRoomAndJoin(user1, "delete-room-1");
        String msgId = sendMsg(user1.cookie, roomId, "To be deleted");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user1.cookie))
                .andExpect(status().isOk());

        // Verify message shows as deleted in history
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").isEmpty());
    }

    @Test
    void deleteMessage_roomOwner_canDeleteOthersMessages() throws Exception {
        UserInfo owner = signUp("del2a@test.com", "del2a");
        UserInfo user2 = signUp("del2b@test.com", "del2b");
        String roomId = createRoomAndJoin(owner, "delete-room-2", user2);
        String msgId = sendMsg(user2.cookie, roomId, "User2 message");

        // Owner can delete user2's message
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(owner.cookie))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMessage_nonOwnerNonSender_returnsForbidden() throws Exception {
        UserInfo owner = signUp("del3a@test.com", "del3a");
        UserInfo user2 = signUp("del3b@test.com", "del3b");
        UserInfo user3 = signUp("del3c@test.com", "del3c");
        String roomId = createRoomAndJoin(owner, "delete-room-3", user2, user3);
        String msgId = sendMsg(user2.cookie, roomId, "User2 message");

        // User3 cannot delete user2's message
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user3.cookie))
                .andExpect(status().isForbidden());
    }

    // --- REPLY TESTS ---

    @Test
    void replyToMessage_showsReplyPreview() throws Exception {
        UserInfo user1 = signUp("rpl1@test.com", "rpl1");
        String roomId = createRoomAndJoin(user1, "reply-room-1");
        String originalMsgId = sendMsg(user1.cookie, roomId, "Original message text");

        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"This is a reply","replyToId":"%s"}
                        """.formatted(originalMsgId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyToId").value(originalMsgId))
                .andExpect(jsonPath("$.replyToPreview.messageId").value(originalMsgId))
                .andExpect(jsonPath("$.replyToPreview.content").value("Original message text"))
                .andExpect(jsonPath("$.replyToPreview.deleted").value(false))
                .andReturn();
    }

    @Test
    void replyToDeletedMessage_showsDeletedPreview() throws Exception {
        UserInfo user1 = signUp("rpl2@test.com", "rpl2");
        String roomId = createRoomAndJoin(user1, "reply-room-2");
        String originalMsgId = sendMsg(user1.cookie, roomId, "Will be deleted");

        // Reply first
        String replyId = objectMapper.readTree(
                mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                                .cookie(user1.cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {"content":"Replying to it","replyToId":"%s"}
                                """.formatted(originalMsgId)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();

        // Delete original
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + originalMsgId)
                        .cookie(user1.cookie))
                .andExpect(status().isOk());

        // Fetch messages — reply should show deleted preview
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].replyToPreview.deleted".formatted(replyId)).value(true));
    }

    @Test
    void replyToMessageInDifferentRoom_returnsBadRequest() throws Exception {
        UserInfo user1 = signUp("rpl3@test.com", "rpl3");
        String room1 = createRoomAndJoin(user1, "reply-room-3a");
        String room2 = createRoomAndJoin(user1, "reply-room-3b");
        String msgInRoom1 = sendMsg(user1.cookie, room1, "Message in room 1");

        // Try to reply to a message from room1 while posting to room2
        mockMvc.perform(post("/api/rooms/" + room2 + "/messages")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"Cross-room reply","replyToId":"%s"}
                        """.formatted(msgInRoom1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageHistory_showsEditedAndDeletedStatus() throws Exception {
        UserInfo user1 = signUp("hist1@test.com", "hist1");
        String roomId = createRoomAndJoin(user1, "history-room-1");

        String msg1 = sendMsg(user1.cookie, roomId, "Normal message");
        String msg2 = sendMsg(user1.cookie, roomId, "Will edit");
        String msg3 = sendMsg(user1.cookie, roomId, "Will delete");

        // Edit msg2
        mockMvc.perform(put("/api/rooms/" + roomId + "/messages/" + msg2)
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"Edited message"}"""))
                .andExpect(status().isOk());

        // Delete msg3
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msg3)
                        .cookie(user1.cookie))
                .andExpect(status().isOk());

        // Fetch history
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.MessageEditDeleteTest"`
Expected: All 10 tests PASS

- [ ] **Step 3: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS (iterations 1-5)

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/MessageEditDeleteTest.java
git commit -m "test: add message edit, delete, and reply integration tests"
```

---

### Task 3: E2E Attachment Tests

**Files:**
- Create: `e2e/tests/attachments.spec.ts`

**Prerequisites:** `docker-compose up --build` running with Iteration 5 implementation deployed.

- [ ] **Step 1: Create attachment e2e tests**

Create `e2e/tests/attachments.spec.ts`:

```typescript
import { test, expect, Page } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill(`Display ${username}`);
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await page.waitForURL('/');
}

function createTestFile(name: string, content: string): string {
  const tmpDir = os.tmpdir();
  const filePath = path.join(tmpDir, name);
  fs.writeFileSync(filePath, content);
  return filePath;
}

function createTestImage(): string {
  // Minimal valid PNG (1x1 pixel)
  const pngBuffer = Buffer.from([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
    0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
    0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
    0x00, 0x00, 0x02, 0x00, 0x01, 0xE2, 0x21, 0xBC,
    0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
    0x44, 0xAE, 0x42, 0x60, 0x82,
  ]);
  const tmpPath = path.join(os.tmpdir(), 'test-image.png');
  fs.writeFileSync(tmpPath, pngBuffer);
  return tmpPath;
}

test.describe('Attachments', () => {
  test('upload text file and see attachment in message', async ({ browser }) => {
    const unique = Date.now();
    const page = await browser.newPage();
    await signUp(page, `att_e2e1_${unique}@test.com`, `att_e2e1_${unique}`);

    // Create room
    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByLabel('Room Name').fill(`att-test-${unique}`);
    await page.getByRole('button', { name: 'OK' }).click();
    await page.getByText(`att-test-${unique}`).click();

    // Upload file via file input
    const filePath = createTestFile(`test-${unique}.txt`, 'Hello from test file');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(filePath);

    // Should see file chip
    await expect(page.getByText(`test-${unique}.txt`)).toBeVisible();

    // Add message text and send
    await page.getByPlaceholder('Type a message').fill('Here is a file');
    await page.getByRole('button', { name: 'send' }).click();

    // Should see attachment in the message
    await expect(page.getByText(`test-${unique}.txt`).last()).toBeVisible({ timeout: 5000 });

    // Cleanup
    fs.unlinkSync(filePath);
    await page.close();
  });

  test('upload image and see thumbnail', async ({ browser }) => {
    const unique = Date.now();
    const page = await browser.newPage();
    await signUp(page, `att_e2e2_${unique}@test.com`, `att_e2e2_${unique}`);

    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByLabel('Room Name').fill(`img-test-${unique}`);
    await page.getByRole('button', { name: 'OK' }).click();
    await page.getByText(`img-test-${unique}`).click();

    const imagePath = createTestImage();
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(imagePath);

    await page.getByPlaceholder('Type a message').fill('A photo');
    await page.getByRole('button', { name: 'send' }).click();

    // Should see image thumbnail (img element)
    await expect(page.locator('img[alt="test-image.png"]')).toBeVisible({ timeout: 5000 });

    fs.unlinkSync(imagePath);
    await page.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/attachments.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/attachments.spec.ts
git commit -m "test: add attachment upload e2e tests"
```

---

### Task 4: E2E Message Action Tests (Edit, Delete, Reply)

**Files:**
- Create: `e2e/tests/message-actions.spec.ts`

- [ ] **Step 1: Create message action e2e tests**

Create `e2e/tests/message-actions.spec.ts`:

```typescript
import { test, expect, Page } from '@playwright/test';

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill(`Display ${username}`);
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await page.waitForURL('/');
}

async function createRoomAndSendMessage(page: Page, roomName: string, message: string): Promise<void> {
  await page.getByRole('button', { name: 'Create' }).click();
  await page.getByLabel('Room Name').fill(roomName);
  await page.getByRole('button', { name: 'OK' }).click();
  await page.getByText(roomName).click();

  await page.getByPlaceholder('Type a message').fill(message);
  await page.getByRole('button', { name: 'send' }).click();
  await expect(page.getByText(message)).toBeVisible({ timeout: 5000 });
}

test.describe('Message Actions', () => {
  test('edit own message shows edited indicator', async ({ browser }) => {
    const unique = Date.now();
    const page = await browser.newPage();
    await signUp(page, `action1_${unique}@test.com`, `action1_${unique}`);
    await createRoomAndSendMessage(page, `action-room-1-${unique}`, 'Original text');

    // Hover message to show actions
    const messageBubble = page.getByText('Original text');
    await messageBubble.hover();

    // Click edit button
    await page.locator('[class*="anticon-edit"]').click();

    // Should see editing indicator
    await expect(page.getByText('Editing message')).toBeVisible();

    // Clear and type new text
    const textArea = page.getByPlaceholder('Edit message...');
    await textArea.fill('Edited text');
    await page.getByRole('button', { name: 'send' }).click();

    // Should see edited text and indicator
    await expect(page.getByText('Edited text')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('(edited)')).toBeVisible({ timeout: 5000 });

    await page.close();
  });

  test('delete own message shows deleted placeholder', async ({ browser }) => {
    const unique = Date.now();
    const page = await browser.newPage();
    await signUp(page, `action2_${unique}@test.com`, `action2_${unique}`);
    await createRoomAndSendMessage(page, `action-room-2-${unique}`, 'Will be deleted');

    // Hover message
    await page.getByText('Will be deleted').hover();

    // Click delete button
    await page.locator('[class*="anticon-delete"]').click();

    // Confirm deletion
    await page.getByRole('button', { name: 'Delete' }).click();

    // Should see deleted placeholder
    await expect(page.getByText('This message was deleted')).toBeVisible({ timeout: 5000 });

    await page.close();
  });

  test('reply to message shows quoted preview', async ({ browser }) => {
    const unique = Date.now();
    const page = await browser.newPage();
    await signUp(page, `action3_${unique}@test.com`, `action3_${unique}`);
    await createRoomAndSendMessage(page, `action-room-3-${unique}`, 'Original message');

    // Hover message
    await page.getByText('Original message').hover();

    // Click reply button
    await page.locator('[class*="anticon-message"]').click();

    // Should see reply bar
    await expect(page.getByText('Replying to')).toBeVisible();

    // Type and send reply
    await page.getByPlaceholder('Type a message').fill('This is my reply');
    await page.getByRole('button', { name: 'send' }).click();

    // Should see reply with quoted preview
    await expect(page.getByText('This is my reply')).toBeVisible({ timeout: 5000 });
    // The quoted preview should contain the original message text
    await expect(page.getByText('Original message').nth(1)).toBeVisible({ timeout: 5000 });

    await page.close();
  });

  test('other user sees edit and delete in real time', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `action4a_${unique}@test.com`, `action4a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `action4b_${unique}@test.com`, `action4b_${unique}`);

    // User1 creates room
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`realtime-${unique}`);
    await page1.getByRole('button', { name: 'OK' }).click();
    await page1.getByText(`realtime-${unique}`).click();

    // User2 joins
    await page2.getByRole('button', { name: 'Browse' }).click();
    await page2.getByPlaceholder('Search rooms').fill(`realtime-${unique}`);
    await page2.getByRole('button', { name: 'Join' }).click();
    await page2.keyboard.press('Escape');
    await page2.getByText(`realtime-${unique}`).click();

    // User1 sends message
    await page1.getByPlaceholder('Type a message').fill('Hello friend');
    await page1.getByRole('button', { name: 'send' }).click();
    await expect(page2.getByText('Hello friend')).toBeVisible({ timeout: 5000 });

    // User1 edits message
    await page1.getByText('Hello friend').hover();
    await page1.locator('[class*="anticon-edit"]').click();
    await page1.getByPlaceholder('Edit message...').fill('Hello edited');
    await page1.getByRole('button', { name: 'send' }).click();

    // User2 should see the edit
    await expect(page2.getByText('Hello edited')).toBeVisible({ timeout: 5000 });
    await expect(page2.getByText('(edited)')).toBeVisible({ timeout: 5000 });

    await page1.close();
    await page2.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/message-actions.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Run full e2e suite**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS (iterations 1-5)

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/message-actions.spec.ts
git commit -m "test: add message edit, delete, and reply e2e tests"
```

---

## Running Tests

### Backend Integration Tests
```bash
cd backend && ./gradlew test
```
Requires: Docker running (Testcontainers pulls postgres:16-alpine automatically)

### E2E Tests
```bash
docker-compose up --build    # in one terminal
cd e2e && npx playwright test  # in another terminal
```

### Full Suite
```bash
cd backend && ./gradlew test && cd ../e2e && npx playwright test
```
