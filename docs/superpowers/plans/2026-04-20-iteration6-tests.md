# Iteration 6: Moderation & Administration — Testing Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend integration tests (JUnit 5 + Testcontainers) and end-to-end tests (Playwright) for all Iteration 6 features: ban/kick, role management, room settings editing, room deletion, and admin message deletion.

**Architecture:** Backend tests use `@SpringBootTest` with `MockMvc` and Testcontainers PostgreSQL — real database, no mocks. Cookie-based session tracking (required for spring-session-jdbc). E2E tests use Playwright against the full docker-compose stack.

**Tech Stack:** JUnit 5, Testcontainers 1.21.4 (PostgreSQL), Spring Boot Test, MockMvc, Playwright, TypeScript

**Prerequisites:** Iteration 6 implementation must be complete. Testcontainers infrastructure and Playwright setup already exist from previous iterations.

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
| `backend/src/test/java/com/chatapp/controller/ModerationTest.java` | Ban/unban, kick, role management, permission hierarchy |
| `backend/src/test/java/com/chatapp/controller/RoomManagementTest.java` | Room update, room deletion, cascade behavior |

### E2E Tests

| File | Responsibility |
|------|----------------|
| `e2e/tests/moderation.spec.ts` | Ban/kick, role changes, admin actions through the UI |

---

### Task 1: Moderation Integration Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/ModerationTest.java`

- [ ] **Step 1: Create ModerationTest**

Create `backend/src/test/java/com/chatapp/controller/ModerationTest.java`:

```java
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
                        .content("""{"role":"ADMIN"}"""))
                .andExpect(status().isOk());
    }

    // --- ROLE MANAGEMENT ---

    @Test
    void promoteToAdmin_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("mod1a@test.com", "mod1a");
        UserInfo user2 = signUp("mod1b@test.com", "mod1b");
        String roomId = createRoom(owner, "mod-room-1");
        joinRoom(user2, roomId);

        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + user2.id + "/role")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"role":"ADMIN"}"""))
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
                        .content("""{"role":"MEMBER"}"""))
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

        // Admin tries to promote user3 — should fail
        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + user3.id + "/role")
                        .cookie(admin.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"role":"ADMIN"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void changeOwnerRole_fails() throws Exception {
        UserInfo owner = signUp("mod4a@test.com", "mod4a");
        String roomId = createRoom(owner, "mod-room-4");

        mockMvc.perform(put("/api/rooms/" + roomId + "/members/" + owner.id + "/role")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"role":"MEMBER"}"""))
                .andExpect(status().isBadRequest());
    }

    // --- BAN ---

    @Test
    void banUser_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("ban1a@test.com", "ban1a");
        UserInfo user2 = signUp("ban1b@test.com", "ban1b");
        String roomId = createRoom(owner, "ban-room-1");
        joinRoom(user2, roomId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":%d,"reason":"spam"}""".formatted(user2.id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(user2.id))
                .andExpect(jsonPath("$.reason").value("spam"));

        // User is no longer a member
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
                        .content("""{"userId":%d}""".formatted(user3.id)))
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
                        .content("""{"userId":%d}""".formatted(user3.id)))
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
                        .content("""{"userId":%d}""".formatted(owner.id)))
                .andExpect(status().isConflict());
    }

    @Test
    void bannedUser_cannotRejoin() throws Exception {
        UserInfo owner = signUp("ban5a@test.com", "ban5a");
        UserInfo user2 = signUp("ban5b@test.com", "ban5b");
        String roomId = createRoom(owner, "ban-room-5");
        joinRoom(user2, roomId);

        // Ban user
        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":%d}""".formatted(user2.id)))
                .andExpect(status().isCreated());

        // Try to rejoin — should fail
        mockMvc.perform(post("/api/rooms/" + roomId + "/join").cookie(user2.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void unbanUser_thenRejoin_succeeds() throws Exception {
        UserInfo owner = signUp("ban6a@test.com", "ban6a");
        UserInfo user2 = signUp("ban6b@test.com", "ban6b");
        String roomId = createRoom(owner, "ban-room-6");
        joinRoom(user2, roomId);

        // Ban
        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":%d}""".formatted(user2.id)))
                .andExpect(status().isCreated());

        // Unban
        mockMvc.perform(delete("/api/rooms/" + roomId + "/bans/" + user2.id)
                        .cookie(owner.cookie))
                .andExpect(status().isOk());

        // Rejoin — should succeed
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
                        .content("""{"userId":%d}""".formatted(user2.id)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":%d}""".formatted(user3.id)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms/" + roomId + "/bans").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // --- KICK (= BAN) ---

    @Test
    void kickMember_asOwner_bansUser() throws Exception {
        UserInfo owner = signUp("kick1a@test.com", "kick1a");
        UserInfo user2 = signUp("kick1b@test.com", "kick1b");
        String roomId = createRoom(owner, "kick-room-1");
        joinRoom(user2, roomId);

        // Kick = DELETE /api/rooms/{roomId}/members/{userId}
        mockMvc.perform(delete("/api/rooms/" + roomId + "/members/" + user2.id)
                        .cookie(owner.cookie))
                .andExpect(status().isOk());

        // User should be banned (cannot rejoin)
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

        // Admin1 bans Admin2
        mockMvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(admin1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":%d}""".formatted(admin2.id)))
                .andExpect(status().isCreated());
    }

    // --- ADMIN MESSAGE DELETION ---

    @Test
    void adminCanDeleteOtherUsersMessage() throws Exception {
        UserInfo owner = signUp("adel1a@test.com", "adel1a");
        UserInfo admin = signUp("adel1b@test.com", "adel1b");
        UserInfo user3 = signUp("adel1c@test.com", "adel1c");
        String roomId = createRoom(owner, "adel-room-1");
        joinRoom(admin, roomId);
        joinRoom(user3, roomId);
        promoteToAdmin(owner, roomId, admin.id);

        // User3 sends a message
        MvcResult msgResult = mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user3.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"Hello from user3"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String msgId = objectMapper.readTree(msgResult.getResponse().getContentAsString()).get("id").asText();

        // Admin deletes the message
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(admin.cookie))
                .andExpect(status().isOk());

        // Verify message is deleted
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
                        .content("""{"content":"Hello"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String msgId = objectMapper.readTree(msgResult.getResponse().getContentAsString()).get("id").asText();

        // User2 (member, not admin) tries to delete — should fail
        mockMvc.perform(delete("/api/rooms/" + roomId + "/messages/" + msgId)
                        .cookie(user2.cookie))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.ModerationTest"`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/ModerationTest.java
git commit -m "test: add moderation integration tests"
```

---

### Task 2: Room Management Integration Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/RoomManagementTest.java`

- [ ] **Step 1: Create RoomManagementTest**

Create `backend/src/test/java/com/chatapp/controller/RoomManagementTest.java`:

```java
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
                        .content("""{"role":"ADMIN"}"""))
                .andExpect(status().isOk());
    }

    // --- ROOM SETTINGS ---

    @Test
    void updateRoom_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("rmgmt1a@test.com", "rmgmt1a");
        String roomId = createRoom(owner, "rmgmt-room-1");

        mockMvc.perform(put("/api/rooms/" + roomId)
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"new-name","description":"new desc"}"""))
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
                        .content("""{"name":"admin-rename","description":"admin desc"}"""))
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
                        .content("""{"name":"hacked-name"}"""))
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
                        .content("""{"name":"taken-name"}"""))
                .andExpect(status().isConflict());
    }

    // --- ROOM DELETION ---

    @Test
    void deleteRoom_asOwner_succeeds() throws Exception {
        UserInfo owner = signUp("rdel1a@test.com", "rdel1a");
        UserInfo user2 = signUp("rdel1b@test.com", "rdel1b");
        String roomId = createRoom(owner, "rdel-room-1");
        joinRoom(user2, roomId);

        // Send a message so there's data to cascade
        mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"Hello"}"""))
                .andExpect(status().isCreated());

        // Delete room
        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isOk());

        // Room should not exist
        mockMvc.perform(get("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isNotFound());
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

        // Send messages
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                            .cookie(owner.cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"content":"Message %d"}""".formatted(i)))
                    .andExpect(status().isCreated());
        }

        // Delete room
        mockMvc.perform(delete("/api/rooms/" + roomId).cookie(owner.cookie))
                .andExpect(status().isOk());

        // Room's data is gone — user's room list should not contain it
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

        // User2's room list should not contain the deleted room
        mockMvc.perform(get("/api/rooms").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(roomId)).doesNotExist());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.RoomManagementTest"`
Expected: All tests PASS

- [ ] **Step 3: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS (iterations 1-6)

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/RoomManagementTest.java
git commit -m "test: add room management integration tests"
```

---

### Task 3: E2E Moderation Tests

**Files:**
- Create: `e2e/tests/moderation.spec.ts`

**Prerequisites:** `docker-compose up --build` running with Iteration 6 implementation deployed.

- [ ] **Step 1: Create moderation e2e tests**

Create `e2e/tests/moderation.spec.ts`:

```typescript
import { test, expect, Page } from '@playwright/test';

const unique = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill(`Test ${username}`);
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

async function createRoom(page: Page, roomName: string): Promise<void> {
  await page.getByRole('button', { name: 'plus Create' }).click();
  await expect(page.locator('.ant-modal')).toBeVisible();
  await page.getByPlaceholder('e.g. general').fill(roomName);
  const responsePromise = page.waitForResponse(resp =>
    resp.url().includes('/api/rooms') && resp.request().method() === 'POST'
  );
  await page.locator('.ant-modal-footer').getByRole('button', { name: 'OK' }).click();
  await responsePromise;
  await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5_000 });
}

async function browseAndJoin(page: Page, roomName: string): Promise<void> {
  await page.getByRole('button', { name: 'Browse' }).click();
  await expect(page.locator('.ant-modal')).toBeVisible();
  await page.getByPlaceholder('Search rooms...').fill(roomName);

  const roomItem = page.locator('.ant-list-item').filter({ hasText: `#${roomName}` });
  await expect(roomItem).toBeVisible({ timeout: 10_000 });

  const joinResponse = page.waitForResponse(resp =>
    resp.url().includes('/api/rooms/') && resp.url().includes('/join') && resp.request().method() === 'POST'
  );
  await roomItem.getByRole('button', { name: 'Join' }).click();
  await joinResponse;
  await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5_000 });
}

test.describe('Moderation', () => {
  test('owner sees manage button and can ban a member', async ({ browser }) => {
    const id = unique();
    const ownerPage = await browser.newPage();
    const memberPage = await browser.newPage();

    await signUp(ownerPage, `modowner_${id}@test.com`, `modowner_${id}`);
    await signUp(memberPage, `modmember_${id}@test.com`, `modmember_${id}`);

    // Owner creates room
    await createRoom(ownerPage, `mod-e2e-${id}`);
    await ownerPage.getByText(`mod-e2e-${id}`).click();

    // Member joins
    await browseAndJoin(memberPage, `mod-e2e-${id}`);
    await memberPage.getByText(`mod-e2e-${id}`).click();

    // Owner clicks Manage
    await expect(ownerPage.getByRole('button', { name: 'Manage' })).toBeVisible();
    await ownerPage.getByRole('button', { name: 'Manage' }).click();

    // Should see the modal with members tab
    await expect(ownerPage.getByText(`Manage: mod-e2e-${id}`)).toBeVisible();
    await expect(ownerPage.getByText(`modmember_${id}`)).toBeVisible();

    // Ban the member
    await ownerPage.getByRole('button', { name: 'Ban' }).click();
    await ownerPage.getByRole('button', { name: 'Ban' }).last().click(); // Confirm popconfirm

    // Should see success message
    await expect(ownerPage.getByText('User banned')).toBeVisible({ timeout: 5_000 });

    await ownerPage.close();
    await memberPage.close();
  });

  test('owner can promote member to admin and demote', async ({ browser }) => {
    const id = unique();
    const ownerPage = await browser.newPage();
    const memberPage = await browser.newPage();

    await signUp(ownerPage, `promowner_${id}@test.com`, `promowner_${id}`);
    await signUp(memberPage, `prommember_${id}@test.com`, `prommember_${id}`);

    await createRoom(ownerPage, `prom-e2e-${id}`);
    await ownerPage.getByText(`prom-e2e-${id}`).click();

    await browseAndJoin(memberPage, `prom-e2e-${id}`);

    // Open manage modal
    await ownerPage.getByRole('button', { name: 'Manage' }).click();
    await expect(ownerPage.getByText(`Manage: prom-e2e-${id}`)).toBeVisible();

    // Promote to admin
    await ownerPage.getByRole('button', { name: 'Make Admin' }).click();
    await expect(ownerPage.getByText('Role updated to ADMIN')).toBeVisible({ timeout: 5_000 });

    // Now demote
    await ownerPage.getByRole('button', { name: 'Remove Admin' }).click();
    await expect(ownerPage.getByText('Role updated to MEMBER')).toBeVisible({ timeout: 5_000 });

    await ownerPage.close();
    await memberPage.close();
  });

  test('owner can update room settings', async ({ page }) => {
    const id = unique();
    await signUp(page, `settings_${id}@test.com`, `settings_${id}`);
    await createRoom(page, `settings-e2e-${id}`);
    await page.getByText(`settings-e2e-${id}`).click();

    await page.getByRole('button', { name: 'Manage' }).click();
    await page.getByRole('tab', { name: 'Settings' }).click();

    // Change room name
    const nameInput = page.locator('.ant-modal input').first();
    await nameInput.clear();
    await nameInput.fill(`renamed-${id}`);

    await page.getByRole('button', { name: 'Save Changes' }).click();
    await expect(page.getByText('Room settings updated')).toBeVisible({ timeout: 5_000 });
  });

  test('owner can delete room', async ({ page }) => {
    const id = unique();
    await signUp(page, `delroom_${id}@test.com`, `delroom_${id}`);
    await createRoom(page, `delroom-e2e-${id}`);
    await page.getByText(`delroom-e2e-${id}`).click();

    await page.getByRole('button', { name: 'Manage' }).click();
    await page.getByRole('tab', { name: 'Settings' }).click();

    // Click delete room
    await page.getByRole('button', { name: 'Delete Room' }).click();
    await page.getByRole('button', { name: 'Delete' }).last().click(); // Confirm

    await expect(page.getByText('Room deleted')).toBeVisible({ timeout: 5_000 });

    // Room should no longer appear in room list
    await expect(page.getByText(`delroom-e2e-${id}`)).not.toBeVisible({ timeout: 5_000 });
  });

  test('member does not see manage button', async ({ browser }) => {
    const id = unique();
    const ownerPage = await browser.newPage();
    const memberPage = await browser.newPage();

    await signUp(ownerPage, `nomng_owner_${id}@test.com`, `nomng_owner_${id}`);
    await signUp(memberPage, `nomng_member_${id}@test.com`, `nomng_member_${id}`);

    await createRoom(ownerPage, `nomng-e2e-${id}`);

    await browseAndJoin(memberPage, `nomng-e2e-${id}`);
    await memberPage.getByText(`nomng-e2e-${id}`).click();

    // Member should NOT see Manage button
    await expect(memberPage.getByRole('button', { name: 'Manage' })).not.toBeVisible();

    await ownerPage.close();
    await memberPage.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/moderation.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Run full e2e suite**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS (iterations 1-6)

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/moderation.spec.ts
git commit -m "test: add moderation e2e tests"
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
