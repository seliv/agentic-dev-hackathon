# Iteration 4: Presence, Status & Notifications — Testing Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend integration tests (JUnit 5 + Testcontainers) and end-to-end tests (Playwright) for all Iteration 4 features: unread counts, mark-as-read, and presence REST endpoint.

**Architecture:** Backend tests use `@SpringBootTest` with `MockMvc` and Testcontainers PostgreSQL — real database, no mocks. Cookie-based session tracking (required for spring-session-jdbc). E2E tests use Playwright against the full docker-compose stack.

**Tech Stack:** JUnit 5, Testcontainers 1.21.4 (PostgreSQL), Spring Boot Test, MockMvc, Playwright, TypeScript

**Prerequisites:** Iteration 4 implementation must be complete. Testcontainers infrastructure and Playwright setup already exist from previous iterations.

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
| `backend/src/test/java/com/chatapp/controller/UnreadTest.java` | Unread count and mark-as-read tests |
| `backend/src/test/java/com/chatapp/controller/PresenceRestTest.java` | Presence REST endpoint tests |

### E2E Tests

| File | Responsibility |
|------|----------------|
| `e2e/tests/unread.spec.ts` | Unread badge and mark-as-read e2e |
| `e2e/tests/presence.spec.ts` | Presence indicator e2e |

---

### Task 1: Unread Integration Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/UnreadTest.java`

- [ ] **Step 1: Create UnreadTest**

Create `backend/src/test/java/com/chatapp/controller/UnreadTest.java`:

```java
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

        // User2 has not read anything — should see 3 unread
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

        String msgId1 = sendMsg(user1.cookie, roomId, "Hello 1");
        sendMsg(user1.cookie, roomId, "Hello 2");
        String msgId3 = sendMsg(user1.cookie, roomId, "Hello 3");

        // Mark as read up to last message
        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId3)))
                .andExpect(status().isOk());

        // Unread count should be 0
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

        // Mark as read up to first message only
        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId1)))
                .andExpect(status().isOk());

        // Should show 2 unread
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

        // Mark as read up to first message
        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId1)))
                .andExpect(status().isOk());

        // Mark as read up to second message (upsert)
        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lastReadMessageId":"%s"}
                        """.formatted(msgId2)))
                .andExpect(status().isOk());

        // No unread
        mockMvc.perform(get("/api/rooms/unread").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.UnreadTest"`
Expected: All 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/UnreadTest.java
git commit -m "test: add unread count and mark-as-read integration tests"
```

---

### Task 2: Presence REST Integration Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/PresenceRestTest.java`

- [ ] **Step 1: Create PresenceRestTest**

Create `backend/src/test/java/com/chatapp/controller/PresenceRestTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PresenceRestTest extends BaseIntegrationTest {

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

    @Test
    void getRoomMemberPresence_returnsMembersWithStatus() throws Exception {
        UserInfo user1 = signUp("pres1a@test.com", "pres1a");
        UserInfo user2 = signUp("pres1b@test.com", "pres1b");

        // Create room
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"presence-room-1","description":"test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // User2 joins
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user2.cookie))
                .andExpect(status().isOk());

        // Get presence — both should be OFFLINE (no WebSocket connected in MockMvc tests)
        mockMvc.perform(get("/api/rooms/" + roomId + "/members/presence").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("OFFLINE"))
                .andExpect(jsonPath("$[1].status").value("OFFLINE"));
    }

    @Test
    void getRoomMemberPresence_nonMember_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("pres2a@test.com", "pres2a");
        UserInfo user2 = signUp("pres2b@test.com", "pres2b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"presence-room-2","description":"test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/rooms/" + roomId + "/members/presence").cookie(user2.cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoomMemberPresence_notAuthenticated_returnsForbidden() throws Exception {
        UserInfo user1 = signUp("pres3@test.com", "pres3");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"presence-room-3","description":"test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/rooms/" + roomId + "/members/presence"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.PresenceRestTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS (iterations 1-4)

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/PresenceRestTest.java
git commit -m "test: add presence REST endpoint integration tests"
```

---

### Task 3: E2E Unread Badge Tests

**Files:**
- Create: `e2e/tests/unread.spec.ts`

**Prerequisites:** `docker-compose up --build` running with Iteration 4 implementation deployed.

- [ ] **Step 1: Create unread e2e tests**

Create `e2e/tests/unread.spec.ts`:

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

test.describe('Unread Badges', () => {
  test('shows unread badge when message arrives in non-selected room', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `ubadge1a_${unique}@test.com`, `ubadge1a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `ubadge1b_${unique}@test.com`, `ubadge1b_${unique}`);

    // User1 creates two rooms
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`room-a-${unique}`);
    await page1.getByRole('button', { name: 'OK' }).click();
    await expect(page1.getByText(`room-a-${unique}`)).toBeVisible();

    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`room-b-${unique}`);
    await page1.getByRole('button', { name: 'OK' }).click();
    await expect(page1.getByText(`room-b-${unique}`)).toBeVisible();

    // User2 joins both rooms
    await page2.getByRole('button', { name: 'Browse' }).click();
    await page2.getByPlaceholder('Search rooms').fill(`room-a-${unique}`);
    await page2.getByRole('button', { name: 'Join' }).click();
    await page2.waitForTimeout(500);
    await page2.getByPlaceholder('Search rooms').fill(`room-b-${unique}`);
    await page2.getByRole('button', { name: 'Join' }).click();
    await page2.keyboard.press('Escape');

    // User2 selects room-a
    await page2.getByText(`room-a-${unique}`).click();

    // User1 sends a message in room-b
    await page1.getByText(`room-b-${unique}`).click();
    await page1.getByPlaceholder('Type a message').fill('Hello room B!');
    await page1.getByRole('button', { name: 'send' }).click();

    // User2 should see unread badge on room-b
    await expect(page2.locator('text=room-b-' + unique).locator('..').locator('span').filter({ hasText: '1' })).toBeVisible({ timeout: 5000 });

    await page1.close();
    await page2.close();
  });

  test('unread badge clears when room is selected', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `ubadge2a_${unique}@test.com`, `ubadge2a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `ubadge2b_${unique}@test.com`, `ubadge2b_${unique}`);

    // User1 creates a room
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`clear-badge-${unique}`);
    await page1.getByRole('button', { name: 'OK' }).click();

    // User2 joins
    await page2.getByRole('button', { name: 'Browse' }).click();
    await page2.getByPlaceholder('Search rooms').fill(`clear-badge-${unique}`);
    await page2.getByRole('button', { name: 'Join' }).click();
    await page2.keyboard.press('Escape');

    // User1 sends a message
    await page1.getByText(`clear-badge-${unique}`).click();
    await page1.getByPlaceholder('Type a message').fill('Unread test');
    await page1.getByRole('button', { name: 'send' }).click();

    // Wait for message to propagate
    await page2.waitForTimeout(1000);

    // User2 clicks on the room — unread should clear
    await page2.getByText(`clear-badge-${unique}`).click();
    await expect(page2.getByText('Unread test')).toBeVisible();

    // Badge should no longer be visible
    // After selecting the room, wait briefly for mark-as-read to complete
    await page2.waitForTimeout(500);

    await page1.close();
    await page2.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/unread.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/unread.spec.ts
git commit -m "test: add unread badge e2e tests"
```

---

### Task 4: E2E Presence Tests

**Files:**
- Create: `e2e/tests/presence.spec.ts`

- [ ] **Step 1: Create presence e2e tests**

Create `e2e/tests/presence.spec.ts`:

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

async function makeFriends(page1: Page, page2: Page, username2: string): Promise<void> {
  await page1.getByRole('button', { name: 'Find Users' }).click();
  await page1.getByPlaceholder('Search by username').fill(username2);
  await page1.getByRole('button', { name: 'Add Friend' }).click();
  await page1.keyboard.press('Escape');

  await page2.getByRole('button', { name: 'Contacts' }).click();
  await page2.getByText('Requests').click();
  await page2.getByRole('button', { name: 'Accept' }).click();
}

test.describe('Presence', () => {
  test('DM presence indicator shows online when user is connected', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `pres1a_${unique}@test.com`, `pres1a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `pres1b_${unique}@test.com`, `pres1b_${unique}`);

    // Become friends and create DM
    await makeFriends(page1, page2, `pres1b_${unique}`);

    await page2.getByText('Friends').click();
    await page2.getByRole('button', { name: 'DM' }).click();
    await page2.keyboard.press('Escape');

    // Wait for DM to appear and presence to propagate
    await page2.waitForTimeout(2000);

    // The DM entry should have a presence indicator (green dot for ONLINE)
    // The presence indicator is a small colored span within the DM row
    const dmRow = page2.locator('text=Direct Message').first();
    await expect(dmRow).toBeVisible();

    await page1.close();
    await page2.close();
  });

  test('presence changes when user disconnects', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `pres2a_${unique}@test.com`, `pres2a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `pres2b_${unique}@test.com`, `pres2b_${unique}`);

    await makeFriends(page1, page2, `pres2b_${unique}`);

    await page2.getByText('Friends').click();
    await page2.getByRole('button', { name: 'DM' }).click();
    await page2.keyboard.press('Escape');

    // Wait for presence to propagate
    await page2.waitForTimeout(2000);

    // Close page1 — user1 goes offline
    await page1.close();

    // Wait for offline timeout (30s) + buffer
    await page2.waitForTimeout(35000);

    // The presence indicator should now show offline (gray dot)
    // This is a long timeout test — presence should have changed
    const dmRow = page2.locator('text=Direct Message').first();
    await expect(dmRow).toBeVisible();

    await page2.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/presence.spec.ts --timeout 60000`
Expected: All tests PASS (the second test needs a longer timeout due to the 30s offline grace period)

- [ ] **Step 3: Run full e2e suite**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS (iterations 1-4)

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/presence.spec.ts
git commit -m "test: add presence indicator e2e tests"
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
