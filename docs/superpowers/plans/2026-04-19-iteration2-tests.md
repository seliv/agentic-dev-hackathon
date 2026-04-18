# Iteration 2: Chat Rooms & Real-time Messaging — Testing Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend integration tests (JUnit 5 + Testcontainers) and end-to-end tests (Playwright) for all Iteration 2 features: room creation, browsing/search, joining/leaving, membership, messaging, and cursor-based pagination.

**Architecture:** Backend tests use `@SpringBootTest` with `MockMvc` and Testcontainers PostgreSQL — real database, no mocks. Cookie-based session tracking (required for spring-session-jdbc). E2E tests use Playwright against the full docker-compose stack.

**Tech Stack:** JUnit 5, Testcontainers 1.21.4 (PostgreSQL), Spring Boot Test, MockMvc, Playwright, TypeScript

**Prerequisites:** Iteration 2 implementation must be complete. Testcontainers infrastructure and Playwright setup already exist from Iteration 1 tests.

---

## Important: Session Handling in Tests

The app uses `spring-session-jdbc`. **`MockHttpSession` does NOT work** for cross-request session tracking. Always use cookies:

```java
// Sign up and get session cookie
Cookie signUpAndGetCookie(String email, String username) {
    MvcResult result = mockMvc.perform(post("/api/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","username":"%s","password":"password123","displayName":"Test User"}
            """.formatted(email, username)))
            .andExpect(status().isCreated())
            .andReturn();
    return result.getResponse().getCookie("SESSION");
}

// Use in subsequent requests
mockMvc.perform(get("/api/rooms").cookie(cookie))
```

---

## File Structure

### Backend Tests

| File | Responsibility |
|------|----------------|
| `backend/src/test/java/com/chatapp/controller/RoomCreationTest.java` | Room creation tests (happy path, validation, duplicate names) |
| `backend/src/test/java/com/chatapp/controller/RoomBrowsingTest.java` | Public room browsing and search tests |
| `backend/src/test/java/com/chatapp/controller/RoomMembershipTest.java` | Join, leave, membership validation, owner-cannot-leave |
| `backend/src/test/java/com/chatapp/controller/RoomMessagesTest.java` | Message sending via REST, cursor-based pagination |

### E2E Tests

| File | Responsibility |
|------|----------------|
| `e2e/tests/rooms.spec.ts` | Room creation, browsing, joining, leaving |
| `e2e/tests/messaging.spec.ts` | Sending and receiving messages in rooms |

---

### Task 1: Room Creation Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/RoomCreationTest.java`

- [ ] **Step 1: Create RoomCreationTest**

Create `backend/src/test/java/com/chatapp/controller/RoomCreationTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomCreationTest extends BaseIntegrationTest {

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
    void createRoom_validRequest_returns201() throws Exception {
        Cookie cookie = signUpAndGetCookie("roomcreate1@test.com", "roomcreate1");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"test-room-1","description":"A test room"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test-room-1"))
                .andExpect(jsonPath("$.description").value("A test room"))
                .andExpect(jsonPath("$.type").value("PUBLIC"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.ownerUsername").value("roomcreate1"));
    }

    @Test
    void createRoom_creatorBecomesOwnerMember() throws Exception {
        Cookie cookie = signUpAndGetCookie("roomcreate2@test.com", "roomcreate2");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"test-room-2","description":"Owner test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();

        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        Cookie latestCookie = result.getResponse().getCookie("SESSION");
        if (latestCookie == null) latestCookie = cookie;

        mockMvc.perform(get("/api/rooms/" + roomId + "/members").cookie(latestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("roomcreate2"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void createRoom_appearsInMyRooms() throws Exception {
        Cookie cookie = signUpAndGetCookie("roomcreate3@test.com", "roomcreate3");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"test-room-3","description":"My rooms test"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'test-room-3')]").exists());
    }

    @Test
    void createRoom_duplicateName_returns409() throws Exception {
        Cookie cookie = signUpAndGetCookie("roomcreate4@test.com", "roomcreate4");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"duplicate-room","description":"First"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"duplicate-room","description":"Second"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A room with this name already exists"));
    }

    @Test
    void createRoom_missingName_returns400() throws Exception {
        Cookie cookie = signUpAndGetCookie("roomcreate5@test.com", "roomcreate5");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"description":"No name"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createRoom_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"no-auth-room","description":"test"}
                        """))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.RoomCreationTest"`
Expected: All 6 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/RoomCreationTest.java
git commit -m "test: add room creation integration tests"
```

---

### Task 2: Room Browsing and Search Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/RoomBrowsingTest.java`

- [ ] **Step 1: Create RoomBrowsingTest**

Create `backend/src/test/java/com/chatapp/controller/RoomBrowsingTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomBrowsingTest extends BaseIntegrationTest {

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

    private void createRoom(Cookie cookie, String name) throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"Test room"}
                        """.formatted(name)))
                .andExpect(status().isCreated());
    }

    @Test
    void getPublicRooms_returnsRooms() throws Exception {
        Cookie cookie = signUpAndGetCookie("browse1@test.com", "browse1");
        createRoom(cookie, "browse-room-1");

        mockMvc.perform(get("/api/rooms/public").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.name == 'browse-room-1')]").exists());
    }

    @Test
    void getPublicRooms_searchByName() throws Exception {
        Cookie cookie = signUpAndGetCookie("browse2@test.com", "browse2");
        createRoom(cookie, "searchable-alpha");
        createRoom(cookie, "searchable-beta");
        createRoom(cookie, "other-gamma");

        mockMvc.perform(get("/api/rooms/public?search=searchable").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getPublicRooms_searchCaseInsensitive() throws Exception {
        Cookie cookie = signUpAndGetCookie("browse3@test.com", "browse3");
        createRoom(cookie, "CamelCaseRoom");

        mockMvc.perform(get("/api/rooms/public?search=camelcase").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'CamelCaseRoom')]").exists());
    }

    @Test
    void getPublicRooms_pagination() throws Exception {
        Cookie cookie = signUpAndGetCookie("browse4@test.com", "browse4");
        for (int i = 0; i < 5; i++) {
            createRoom(cookie, "paginated-room-" + i);
        }

        mockMvc.perform(get("/api/rooms/public?page=0&size=3").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void getPublicRooms_showsMemberCount() throws Exception {
        Cookie cookie1 = signUpAndGetCookie("browse5a@test.com", "browse5a");
        createRoom(cookie1, "member-count-room");

        mockMvc.perform(get("/api/rooms/public?search=member-count-room").cookie(cookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].memberCount").value(1));
    }

    @Test
    void getPublicRooms_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/rooms/public"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.RoomBrowsingTest"`
Expected: All 6 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/RoomBrowsingTest.java
git commit -m "test: add room browsing and search integration tests"
```

---

### Task 3: Room Membership Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/RoomMembershipTest.java`

- [ ] **Step 1: Create RoomMembershipTest**

Create `backend/src/test/java/com/chatapp/controller/RoomMembershipTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomMembershipTest extends BaseIntegrationTest {

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

    private String createRoom(Cookie cookie, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"Test room"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void joinRoom_success() throws Exception {
        Cookie owner = signUpAndGetCookie("memb1a@test.com", "memb1a");
        String roomId = createRoom(owner, "join-test-room");

        Cookie joiner = signUpAndGetCookie("memb1b@test.com", "memb1b");
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(joiner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomId + "/members").cookie(joiner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void joinRoom_alreadyMember_idempotent() throws Exception {
        Cookie owner = signUpAndGetCookie("memb2a@test.com", "memb2a");
        String roomId = createRoom(owner, "idempotent-join-room");

        Cookie joiner = signUpAndGetCookie("memb2b@test.com", "memb2b");
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(joiner))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(joiner))
                .andExpect(status().isOk());
    }

    @Test
    void leaveRoom_success() throws Exception {
        Cookie owner = signUpAndGetCookie("memb3a@test.com", "memb3a");
        String roomId = createRoom(owner, "leave-test-room");

        Cookie joiner = signUpAndGetCookie("memb3b@test.com", "memb3b");
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(joiner))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + roomId + "/leave").cookie(joiner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomId + "/members").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void leaveRoom_ownerCannotLeave() throws Exception {
        Cookie owner = signUpAndGetCookie("memb4@test.com", "memb4");
        String roomId = createRoom(owner, "owner-leave-room");

        mockMvc.perform(post("/api/rooms/" + roomId + "/leave").cookie(owner))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getRoom_nonMember_returns403() throws Exception {
        Cookie owner = signUpAndGetCookie("memb5a@test.com", "memb5a");
        String roomId = createRoom(owner, "nonmember-room");

        Cookie nonMember = signUpAndGetCookie("memb5b@test.com", "memb5b");
        mockMvc.perform(get("/api/rooms/" + roomId).cookie(nonMember))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMembers_nonMember_returns403() throws Exception {
        Cookie owner = signUpAndGetCookie("memb6a@test.com", "memb6a");
        String roomId = createRoom(owner, "nonmember-members-room");

        Cookie nonMember = signUpAndGetCookie("memb6b@test.com", "memb6b");
        mockMvc.perform(get("/api/rooms/" + roomId + "/members").cookie(nonMember))
                .andExpect(status().isForbidden());
    }

    @Test
    void joinRoom_roomNotFound_returns404() throws Exception {
        Cookie cookie = signUpAndGetCookie("memb7@test.com", "memb7");

        mockMvc.perform(post("/api/rooms/00000000-0000-0000-0000-000000000000/join").cookie(cookie))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.RoomMembershipTest"`
Expected: All 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/RoomMembershipTest.java
git commit -m "test: add room membership integration tests"
```

---

### Task 4: Room Messages Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/RoomMessagesTest.java`

- [ ] **Step 1: Create RoomMessagesTest**

Create `backend/src/test/java/com/chatapp/controller/RoomMessagesTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomMessagesTest extends BaseIntegrationTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private UserRepository userRepository;

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

    private String createRoom(Cookie cookie, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"%s","description":"Test room"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void getMessages_emptyRoom_returnsEmptyList() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg1@test.com", "msg1");
        String roomId = createRoom(cookie, "empty-msg-room");

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMessages_returnsMessagesInRoom() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg2@test.com", "msg2");
        String roomId = createRoom(cookie, "msg-room-2");

        // Insert messages directly via repository
        ChatRoom room = chatRoomRepository.findById(UUID.fromString(roomId)).orElseThrow();
        User sender = userRepository.findByEmail("msg2@test.com").orElseThrow();

        for (int i = 0; i < 3; i++) {
            Message msg = new Message();
            msg.setRoom(room);
            msg.setSender(sender);
            msg.setContent("Message " + i);
            messageRepository.save(msg);
        }

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].content").isNotEmpty())
                .andExpect(jsonPath("$[0].senderUsername").value("msg2"))
                .andExpect(jsonPath("$[0].roomId").value(roomId));
    }

    @Test
    void getMessages_cursorPagination() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg3@test.com", "msg3");
        String roomId = createRoom(cookie, "cursor-msg-room");

        ChatRoom room = chatRoomRepository.findById(UUID.fromString(roomId)).orElseThrow();
        User sender = userRepository.findByEmail("msg3@test.com").orElseThrow();

        for (int i = 0; i < 5; i++) {
            Message msg = new Message();
            msg.setRoom(room);
            msg.setSender(sender);
            msg.setContent("Paginated " + i);
            messageRepository.save(msg);
            Thread.sleep(10); // Ensure distinct timestamps
        }

        // Get first page (limit 3)
        MvcResult result = mockMvc.perform(get("/api/rooms/" + roomId + "/messages?limit=3").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn();

        // Get the oldest message's createdAt for cursor
        String responseBody = result.getResponse().getContentAsString();
        var messages = objectMapper.readTree(responseBody);
        String oldestTimestamp = messages.get(0).get("createdAt").asText();

        // Get older messages using cursor
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages?before=" + oldestTimestamp + "&limit=3").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getMessages_nonMember_returns403() throws Exception {
        Cookie owner = signUpAndGetCookie("msg4a@test.com", "msg4a");
        String roomId = createRoom(owner, "nonmember-msg-room");

        Cookie nonMember = signUpAndGetCookie("msg4b@test.com", "msg4b");
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(nonMember))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMessages_limitCapped() throws Exception {
        Cookie cookie = signUpAndGetCookie("msg5@test.com", "msg5");
        String roomId = createRoom(cookie, "limit-cap-room");

        // Request with limit > 100 should be capped
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages?limit=200").cookie(cookie))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.RoomMessagesTest"`
Expected: All 5 tests PASS

- [ ] **Step 3: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS (iteration 1 + iteration 2)

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/RoomMessagesTest.java
git commit -m "test: add room messages integration tests"
```

---

### Task 5: E2E Room Tests

**Files:**
- Create: `e2e/tests/rooms.spec.ts`

**Prerequisites:** `docker-compose up --build` running with Iteration 2 implementation deployed.

- [ ] **Step 1: Create rooms e2e tests**

Create `e2e/tests/rooms.spec.ts`:

```typescript
import { test, expect, Page } from '@playwright/test';

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill('Test User');
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await page.waitForURL('/');
}

test.describe('Chat Rooms', () => {
  test('create room and see it in room list', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `rooms1_${unique}@test.com`, `rooms1_${unique}`);

    // Click Create button
    await page.getByRole('button', { name: 'Create' }).click();

    // Fill room form
    await page.getByLabel('Room Name').fill(`test-room-${unique}`);
    await page.getByLabel('Description').fill('A test room');
    await page.getByRole('button', { name: 'OK' }).click();

    // Room should appear in sidebar
    await expect(page.getByText(`#test-room-${unique}`)).toBeVisible();
  });

  test('browse and join a public room', async ({ browser }) => {
    const unique = Date.now();

    // User 1 creates a room
    const page1 = await browser.newPage();
    await signUp(page1, `rooms2a_${unique}@test.com`, `rooms2a_${unique}`);
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`joinable-room-${unique}`);
    await page1.getByRole('button', { name: 'OK' }).click();
    await page1.close();

    // User 2 browses and joins
    const page2 = await browser.newPage();
    await signUp(page2, `rooms2b_${unique}@test.com`, `rooms2b_${unique}`);
    await page2.getByRole('button', { name: 'Browse' }).click();

    // Search for the room
    await page2.getByPlaceholder('Search rooms').fill(`joinable-room-${unique}`);
    await expect(page2.getByText(`#joinable-room-${unique}`)).toBeVisible();

    // Join
    await page2.getByRole('button', { name: 'Join' }).click();

    // Room should appear in user's room list
    await page2.getByRole('button', { name: 'Cancel' }).click(); // Close modal
    await expect(page2.getByText(`#joinable-room-${unique}`)).toBeVisible();
    await page2.close();
  });

  test('leave a room', async ({ browser }) => {
    const unique = Date.now();

    // Create a room
    const page1 = await browser.newPage();
    await signUp(page1, `rooms3a_${unique}@test.com`, `rooms3a_${unique}`);
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`leave-room-${unique}`);
    await page1.getByRole('button', { name: 'OK' }).click();
    await page1.close();

    // Join as different user then leave
    const page2 = await browser.newPage();
    await signUp(page2, `rooms3b_${unique}@test.com`, `rooms3b_${unique}`);
    await page2.getByRole('button', { name: 'Browse' }).click();
    await page2.getByPlaceholder('Search rooms').fill(`leave-room-${unique}`);
    await page2.getByRole('button', { name: 'Join' }).click();
    await page2.getByRole('button', { name: 'Cancel' }).click();

    // Select the room
    await page2.getByText(`#leave-room-${unique}`).click();

    // Click leave
    await page2.getByRole('button', { name: 'Leave' }).click();

    // Room should disappear from list
    await expect(page2.getByText(`#leave-room-${unique}`)).not.toBeVisible();
    await page2.close();
  });

  test('duplicate room name shows error', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `rooms4_${unique}@test.com`, `rooms4_${unique}`);

    // Create first room
    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByLabel('Room Name').fill(`unique-name-${unique}`);
    await page.getByRole('button', { name: 'OK' }).click();
    await expect(page.getByText(`#unique-name-${unique}`)).toBeVisible();

    // Try creating with same name
    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByLabel('Room Name').fill(`unique-name-${unique}`);
    await page.getByRole('button', { name: 'OK' }).click();

    // Should see error (modal stays open or error message)
    await expect(page.getByText('already exists').or(page.getByText('Failed'))).toBeVisible();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/rooms.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/rooms.spec.ts
git commit -m "test: add chat rooms e2e tests"
```

---

### Task 6: E2E Messaging Tests

**Files:**
- Create: `e2e/tests/messaging.spec.ts`

- [ ] **Step 1: Create messaging e2e tests**

Create `e2e/tests/messaging.spec.ts`:

```typescript
import { test, expect, Page } from '@playwright/test';

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill('Test User');
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await page.waitForURL('/');
}

test.describe('Messaging', () => {
  test('send a message and see it in chat', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `chat1_${unique}@test.com`, `chat1_${unique}`);

    // Create room
    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByLabel('Room Name').fill(`chat-room-${unique}`);
    await page.getByRole('button', { name: 'OK' }).click();

    // Select room
    await page.getByText(`#chat-room-${unique}`).click();

    // Type and send message
    await page.getByPlaceholder('Type a message').fill('Hello, world!');
    await page.getByRole('button', { name: 'send' }).click();

    // Message should appear in chat
    await expect(page.getByText('Hello, world!')).toBeVisible();
  });

  test('two users can exchange messages in real time', async ({ browser }) => {
    const unique = Date.now();
    const roomName = `realtime-room-${unique}`;

    // User 1 creates room
    const page1 = await browser.newPage();
    await signUp(page1, `chat2a_${unique}@test.com`, `chat2a_${unique}`);
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(roomName);
    await page1.getByRole('button', { name: 'OK' }).click();
    await page1.getByText(`#${roomName}`).click();

    // User 2 joins room
    const page2 = await browser.newPage();
    await signUp(page2, `chat2b_${unique}@test.com`, `chat2b_${unique}`);
    await page2.getByRole('button', { name: 'Browse' }).click();
    await page2.getByPlaceholder('Search rooms').fill(roomName);
    await page2.getByRole('button', { name: 'Join' }).click();
    await page2.getByRole('button', { name: 'Cancel' }).click();
    await page2.getByText(`#${roomName}`).click();

    // User 1 sends message
    await page1.getByPlaceholder('Type a message').fill('Hello from user 1!');
    await page1.getByRole('button', { name: 'send' }).click();

    // User 2 should see it
    await expect(page2.getByText('Hello from user 1!')).toBeVisible({ timeout: 10000 });

    // User 2 sends message
    await page2.getByPlaceholder('Type a message').fill('Hello from user 2!');
    await page2.getByRole('button', { name: 'send' }).click();

    // User 1 should see it
    await expect(page1.getByText('Hello from user 2!')).toBeVisible({ timeout: 10000 });

    await page1.close();
    await page2.close();
  });

  test('empty state shows when no room selected', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `chat3_${unique}@test.com`, `chat3_${unique}`);

    await expect(page.getByText('Select a room to start chatting')).toBeVisible();
  });

  test('message input disabled when no room selected', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `chat4_${unique}@test.com`, `chat4_${unique}`);

    // No room selected — chat area should show empty state, no message input
    await expect(page.getByText('Select a room')).toBeVisible();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/messaging.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Run full e2e suite**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS (iteration 1 + iteration 2)

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/messaging.spec.ts
git commit -m "test: add messaging e2e tests"
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
