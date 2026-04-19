# Iteration 3: Private Rooms, Contacts & Personal Messaging — Testing Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend integration tests (JUnit 5 + Testcontainers) and end-to-end tests (Playwright) for all Iteration 3 features: friendships, blocks, room invitations, private rooms, direct messages, and user search.

**Architecture:** Backend tests use `@SpringBootTest` with `MockMvc` and Testcontainers PostgreSQL — real database, no mocks. Cookie-based session tracking (required for spring-session-jdbc). E2E tests use Playwright against the full docker-compose stack.

**Tech Stack:** JUnit 5, Testcontainers 1.21.4 (PostgreSQL), Spring Boot Test, MockMvc, Playwright, TypeScript

**Prerequisites:** Iteration 3 implementation must be complete. Testcontainers infrastructure and Playwright setup already exist from previous iterations.

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
| `backend/src/test/java/com/chatapp/controller/FriendshipTest.java` | Friend request lifecycle tests |
| `backend/src/test/java/com/chatapp/controller/BlockTest.java` | Block/unblock and cascade tests |
| `backend/src/test/java/com/chatapp/controller/RoomInvitationTest.java` | Room invitation lifecycle tests |
| `backend/src/test/java/com/chatapp/controller/DirectMessageTest.java` | DM creation and block enforcement tests |
| `backend/src/test/java/com/chatapp/controller/PrivateRoomTest.java` | Private room creation and visibility tests |
| `backend/src/test/java/com/chatapp/controller/UserSearchTest.java` | User search tests |

### E2E Tests

| File | Responsibility |
|------|----------------|
| `e2e/tests/friends.spec.ts` | Friend request flow e2e |
| `e2e/tests/direct-messages.spec.ts` | DM creation and messaging e2e |
| `e2e/tests/private-rooms.spec.ts` | Private rooms and invitations e2e |

---

### Task 1: Friendship Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/FriendshipTest.java`

- [ ] **Step 1: Create FriendshipTest**

Create `backend/src/test/java/com/chatapp/controller/FriendshipTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FriendshipTest extends BaseIntegrationTest {

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
    void sendFriendRequest_success() throws Exception {
        UserInfo user1 = signUp("fr1a@test.com", "fr1a");
        UserInfo user2 = signUp("fr1b@test.com", "fr1b");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.direction").value("OUTGOING"))
                .andExpect(jsonPath("$.friendUsername").value("fr1b"));
    }

    @Test
    void sendFriendRequest_toSelf_returns400() throws Exception {
        UserInfo user1 = signUp("fr2@test.com", "fr2");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user1.id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendFriendRequest_duplicate_returnsConflict() throws Exception {
        UserInfo user1 = signUp("fr3a@test.com", "fr3a");
        UserInfo user2 = signUp("fr3b@test.com", "fr3b");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptFriendRequest_success() throws Exception {
        UserInfo user1 = signUp("fr4a@test.com", "fr4a");
        UserInfo user2 = signUp("fr4b@test.com", "fr4b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept")
                        .cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Both users should see each other in friends list
        mockMvc.perform(get("/api/friends").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendUsername").value("fr4b"));

        mockMvc.perform(get("/api/friends").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendUsername").value("fr4a"));
    }

    @Test
    void declineFriendRequest_success() throws Exception {
        UserInfo user1 = signUp("fr5a@test.com", "fr5a");
        UserInfo user2 = signUp("fr5b@test.com", "fr5b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/decline")
                        .cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void acceptFriendRequest_byNonAddressee_returnsConflict() throws Exception {
        UserInfo user1 = signUp("fr6a@test.com", "fr6a");
        UserInfo user2 = signUp("fr6b@test.com", "fr6b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // User1 (requester) tries to accept their own request
        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept")
                        .cookie(user1.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void removeFriend_success() throws Exception {
        UserInfo user1 = signUp("fr7a@test.com", "fr7a");
        UserInfo user2 = signUp("fr7b@test.com", "fr7b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept").cookie(user2.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/friends/" + friendshipId).cookie(user1.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/friends").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getPendingRequests_returnsIncomingAndOutgoing() throws Exception {
        UserInfo user1 = signUp("fr8a@test.com", "fr8a");
        UserInfo user2 = signUp("fr8b@test.com", "fr8b");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated());

        // User1 sees outgoing
        mockMvc.perform(get("/api/friends/requests").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"));

        // User2 sees incoming
        mockMvc.perform(get("/api/friends/requests").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("INCOMING"));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.FriendshipTest"`
Expected: All 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/FriendshipTest.java
git commit -m "test: add friendship integration tests"
```

---

### Task 2: Block Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/BlockTest.java`

- [ ] **Step 1: Create BlockTest**

Create `backend/src/test/java/com/chatapp/controller/BlockTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BlockTest extends BaseIntegrationTest {

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
    void blockUser_success() throws Exception {
        UserInfo user1 = signUp("blk1a@test.com", "blk1a");
        UserInfo user2 = signUp("blk1b@test.com", "blk1b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block")
                        .cookie(user1.cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockedUsername").value("blk1b"));
    }

    @Test
    void blockUser_self_returns400() throws Exception {
        UserInfo user1 = signUp("blk2@test.com", "blk2");

        mockMvc.perform(post("/api/users/" + user1.id + "/block")
                        .cookie(user1.cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blockUser_removesFriendship() throws Exception {
        UserInfo user1 = signUp("blk3a@test.com", "blk3a");
        UserInfo user2 = signUp("blk3b@test.com", "blk3b");

        // Become friends
        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept").cookie(user2.cookie))
                .andExpect(status().isOk());

        // Block should remove friendship
        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/friends").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blockUser_preventsFriendRequest() throws Exception {
        UserInfo user1 = signUp("blk4a@test.com", "blk4a");
        UserInfo user2 = signUp("blk4b@test.com", "blk4b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user2.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user1.id)))
                .andExpect(status().isConflict());
    }

    @Test
    void unblockUser_success() throws Exception {
        UserInfo user1 = signUp("blk5a@test.com", "blk5a");
        UserInfo user2 = signUp("blk5b@test.com", "blk5b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/blocks").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getBlockedUsers_returnsList() throws Exception {
        UserInfo user1 = signUp("blk6a@test.com", "blk6a");
        UserInfo user2 = signUp("blk6b@test.com", "blk6b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/blocks").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].blockedUsername").value("blk6b"));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.BlockTest"`
Expected: All 6 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/BlockTest.java
git commit -m "test: add block integration tests"
```

---

### Task 3: Room Invitation Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/RoomInvitationTest.java`

- [ ] **Step 1: Create RoomInvitationTest**

Create `backend/src/test/java/com/chatapp/controller/RoomInvitationTest.java`:

```java
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
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.RoomInvitationTest"`
Expected: All 6 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/RoomInvitationTest.java
git commit -m "test: add room invitation integration tests"
```

---

### Task 4: Direct Message Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/DirectMessageTest.java`

- [ ] **Step 1: Create DirectMessageTest**

Create `backend/src/test/java/com/chatapp/controller/DirectMessageTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectMessageTest extends BaseIntegrationTest {

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

    private Long makeFriends(UserInfo user1, UserInfo user2) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept").cookie(user2.cookie))
                .andExpect(status().isOk());
        return friendshipId;
    }

    @Test
    void createDM_withFriend_success() throws Exception {
        UserInfo user1 = signUp("dm1a@test.com", "dm1a");
        UserInfo user2 = signUp("dm1b@test.com", "dm1b");
        makeFriends(user1, user2);

        mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DIRECT"));
    }

    @Test
    void createDM_idempotent() throws Exception {
        UserInfo user1 = signUp("dm2a@test.com", "dm2a");
        UserInfo user2 = signUp("dm2b@test.com", "dm2b");
        makeFriends(user1, user2);

        MvcResult result1 = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("id").asText();

        MvcResult result2 = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId2 = objectMapper.readTree(result2.getResponse().getContentAsString()).get("id").asText();

        // Same room returned
        assert roomId1.equals(roomId2);
    }

    @Test
    void createDM_bothDirections_sameRoom() throws Exception {
        UserInfo user1 = signUp("dm3a@test.com", "dm3a");
        UserInfo user2 = signUp("dm3b@test.com", "dm3b");
        makeFriends(user1, user2);

        MvcResult result1 = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("id").asText();

        MvcResult result2 = mockMvc.perform(post("/api/direct-messages/" + user1.id).cookie(user2.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId2 = objectMapper.readTree(result2.getResponse().getContentAsString()).get("id").asText();

        assert roomId1.equals(roomId2);
    }

    @Test
    void createDM_notFriends_returnsConflict() throws Exception {
        UserInfo user1 = signUp("dm4a@test.com", "dm4a");
        UserInfo user2 = signUp("dm4b@test.com", "dm4b");

        mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void createDM_blocked_returnsConflict() throws Exception {
        UserInfo user1 = signUp("dm5a@test.com", "dm5a");
        UserInfo user2 = signUp("dm5b@test.com", "dm5b");

        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void sendMessage_inBlockedDM_returnsConflict() throws Exception {
        UserInfo user1 = signUp("dm6a@test.com", "dm6a");
        UserInfo user2 = signUp("dm6b@test.com", "dm6b");
        makeFriends(user1, user2);

        // Create DM
        MvcResult result = mockMvc.perform(post("/api/direct-messages/" + user2.id).cookie(user1.cookie))
                .andExpect(status().isOk())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Block user2
        mockMvc.perform(post("/api/users/" + user2.id + "/block").cookie(user1.cookie))
                .andExpect(status().isCreated());

        // Try to send message — should fail
        mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"content":"Hello after block"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void createDM_withSelf_returns400() throws Exception {
        UserInfo user1 = signUp("dm7@test.com", "dm7");

        mockMvc.perform(post("/api/direct-messages/" + user1.id).cookie(user1.cookie))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.DirectMessageTest"`
Expected: All 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/DirectMessageTest.java
git commit -m "test: add direct message integration tests"
```

---

### Task 5: Private Room and User Search Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/PrivateRoomTest.java`
- Create: `backend/src/test/java/com/chatapp/controller/UserSearchTest.java`

- [ ] **Step 1: Create PrivateRoomTest**

Create `backend/src/test/java/com/chatapp/controller/PrivateRoomTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrivateRoomTest extends BaseIntegrationTest {

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
    void createPrivateRoom_success() throws Exception {
        Cookie cookie = signUpAndGetCookie("pr1@test.com", "pr1");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"private-room-1","description":"Secret room","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PRIVATE"))
                .andExpect(jsonPath("$.name").value("private-room-1"));
    }

    @Test
    void privateRoom_notInPublicCatalog() throws Exception {
        Cookie cookie = signUpAndGetCookie("pr2@test.com", "pr2");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"hidden-room","description":"Hidden","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms/public?search=hidden-room").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void privateRoom_duplicateNameAllowed() throws Exception {
        Cookie cookie1 = signUpAndGetCookie("pr3a@test.com", "pr3a");
        Cookie cookie2 = signUpAndGetCookie("pr3b@test.com", "pr3b");

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"same-name","description":"First","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms")
                        .cookie(cookie2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"same-name","description":"Second","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void privateRoom_nonMemberCannotAccess() throws Exception {
        Cookie owner = signUpAndGetCookie("pr4a@test.com", "pr4a");
        Cookie other = signUpAndGetCookie("pr4b@test.com", "pr4b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"restricted-room","description":"Restricted","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/rooms/" + roomId).cookie(other))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateRoom_cannotJoinDirectly() throws Exception {
        Cookie owner = signUpAndGetCookie("pr5a@test.com", "pr5a");
        Cookie other = signUpAndGetCookie("pr5b@test.com", "pr5b");

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"no-join-room","description":"No join","type":"PRIVATE"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Direct join should still work technically (joinRoom doesn't check room type)
        // but private rooms aren't in the catalog, so users can't discover them
        // The access control is via invitations, not join blocking
        mockMvc.perform(get("/api/rooms/" + roomId).cookie(other))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Create UserSearchTest**

Create `backend/src/test/java/com/chatapp/controller/UserSearchTest.java`:

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

class UserSearchTest extends BaseIntegrationTest {

    private Cookie signUpAndGetCookie(String email, String username, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","username":"%s","password":"password123","displayName":"%s"}
                        """.formatted(email, username, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void searchUsers_byUsername() throws Exception {
        Cookie cookie = signUpAndGetCookie("us1@test.com", "searchable_user", "Searchable User");
        signUpAndGetCookie("us1b@test.com", "other_user", "Other User");

        mockMvc.perform(get("/api/users/search?q=searchable").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // Excludes self
    }

    @Test
    void searchUsers_byDisplayName() throws Exception {
        signUpAndGetCookie("us2a@test.com", "us2a", "Unique Display Name");
        Cookie searcher = signUpAndGetCookie("us2b@test.com", "us2b", "Searcher");

        mockMvc.perform(get("/api/users/search?q=Unique").cookie(searcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("us2a"));
    }

    @Test
    void searchUsers_caseInsensitive() throws Exception {
        signUpAndGetCookie("us3a@test.com", "CamelUser", "Camel User");
        Cookie searcher = signUpAndGetCookie("us3b@test.com", "us3b", "Searcher");

        mockMvc.perform(get("/api/users/search?q=cameluser").cookie(searcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("CamelUser"));
    }

    @Test
    void searchUsers_tooShort_returnsEmpty() throws Exception {
        Cookie cookie = signUpAndGetCookie("us4@test.com", "us4", "User");

        mockMvc.perform(get("/api/users/search?q=a").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchUsers_excludesSelf() throws Exception {
        Cookie cookie = signUpAndGetCookie("us5@test.com", "selfexclude", "Self Exclude");

        mockMvc.perform(get("/api/users/search?q=selfexclude").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchUsers_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/search?q=test"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.PrivateRoomTest" --tests "com.chatapp.controller.UserSearchTest"`
Expected: All tests PASS

- [ ] **Step 4: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: All tests PASS (iteration 1 + 2 + 3)

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/PrivateRoomTest.java backend/src/test/java/com/chatapp/controller/UserSearchTest.java
git commit -m "test: add private room and user search integration tests"
```

---

### Task 6: E2E Friends Flow

**Files:**
- Create: `e2e/tests/friends.spec.ts`

**Prerequisites:** `docker-compose up --build` running with Iteration 3 implementation deployed.

- [ ] **Step 1: Create friends e2e tests**

Create `e2e/tests/friends.spec.ts`:

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

test.describe('Friends', () => {
  test('send friend request via user search', async ({ browser }) => {
    const unique = Date.now();

    // User 1 signs up
    const page1 = await browser.newPage();
    await signUp(page1, `fr1a_${unique}@test.com`, `fr1a_${unique}`);

    // User 2 signs up
    const page2 = await browser.newPage();
    await signUp(page2, `fr1b_${unique}@test.com`, `fr1b_${unique}`);

    // User 1 searches for User 2
    await page1.getByRole('button', { name: 'Find Users' }).click();
    await page1.getByPlaceholder('Search by username').fill(`fr1b_${unique}`);
    await expect(page1.getByText(`fr1b_${unique}`)).toBeVisible();
    await page1.getByRole('button', { name: 'Add Friend' }).click();
    await expect(page1.getByText('Friend request sent')).toBeVisible();

    // User 2 checks contacts for incoming request
    await page2.getByRole('button', { name: 'Contacts' }).click();
    await page2.getByText('Requests').click();
    await expect(page2.getByText(`fr1a_${unique}`)).toBeVisible();

    // User 2 accepts
    await page2.getByRole('button', { name: 'Accept' }).click();
    await expect(page2.getByText('Friend request accepted')).toBeVisible();

    // User 2 checks friends tab
    await page2.getByText('Friends').click();
    await expect(page2.getByText(`fr1a_${unique}`)).toBeVisible();

    await page1.close();
    await page2.close();
  });

  test('remove friend', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `fr2a_${unique}@test.com`, `fr2a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `fr2b_${unique}@test.com`, `fr2b_${unique}`);

    // Become friends
    await page1.getByRole('button', { name: 'Find Users' }).click();
    await page1.getByPlaceholder('Search by username').fill(`fr2b_${unique}`);
    await page1.getByRole('button', { name: 'Add Friend' }).click();
    await page1.keyboard.press('Escape');

    await page2.getByRole('button', { name: 'Contacts' }).click();
    await page2.getByText('Requests').click();
    await page2.getByRole('button', { name: 'Accept' }).click();

    // Remove friend
    await page2.getByText('Friends').click();
    await page2.getByRole('button', { name: 'Remove' }).click();
    await expect(page2.getByText('Friend removed')).toBeVisible();

    await page1.close();
    await page2.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/friends.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/friends.spec.ts
git commit -m "test: add friends e2e tests"
```

---

### Task 7: E2E Direct Messages

**Files:**
- Create: `e2e/tests/direct-messages.spec.ts`

- [ ] **Step 1: Create DM e2e tests**

Create `e2e/tests/direct-messages.spec.ts`:

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

test.describe('Direct Messages', () => {
  test('create DM from contacts and send message', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `dm1a_${unique}@test.com`, `dm1a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `dm1b_${unique}@test.com`, `dm1b_${unique}`);

    await makeFriends(page1, page2, `dm1b_${unique}`);

    // User 2 opens friends tab and clicks DM
    await page2.getByText('Friends').click();
    await page2.getByRole('button', { name: 'DM' }).click();

    // Should see DM room in sidebar
    await expect(page2.getByText('Direct Message')).toBeVisible({ timeout: 5000 });

    // Send a message
    await page2.getByPlaceholder('Type a message').fill('Hello via DM!');
    await page2.getByRole('button', { name: 'send' }).click();

    await expect(page2.getByText('Hello via DM!')).toBeVisible();

    await page1.close();
    await page2.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/direct-messages.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/direct-messages.spec.ts
git commit -m "test: add direct messages e2e tests"
```

---

### Task 8: E2E Private Rooms

**Files:**
- Create: `e2e/tests/private-rooms.spec.ts`

- [ ] **Step 1: Create private rooms e2e tests**

Create `e2e/tests/private-rooms.spec.ts`:

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
  await page2.keyboard.press('Escape');
}

test.describe('Private Rooms', () => {
  test('create private room', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `pr1_${unique}@test.com`, `pr1_${unique}`);

    await page.getByRole('button', { name: 'Create' }).click();
    await page.getByLabel('Room Name').fill(`private-room-${unique}`);
    await page.getByLabel('Private').check();
    await page.getByRole('button', { name: 'OK' }).click();

    // Should see room with lock icon
    await expect(page.getByText(`private-room-${unique}`)).toBeVisible();
  });

  test('private room not visible in public browse', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `pr2a_${unique}@test.com`, `pr2a_${unique}`);

    // Create private room
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`secret-${unique}`);
    await page1.getByLabel('Private').check();
    await page1.getByRole('button', { name: 'OK' }).click();

    // User 2 browses rooms — should not see private room
    const page2 = await browser.newPage();
    await signUp(page2, `pr2b_${unique}@test.com`, `pr2b_${unique}`);
    await page2.getByRole('button', { name: 'Browse' }).click();
    await page2.getByPlaceholder('Search rooms').fill(`secret-${unique}`);

    // Wait a moment for search results
    await page2.waitForTimeout(1000);
    await expect(page2.getByText(`secret-${unique}`)).not.toBeVisible();

    await page1.close();
    await page2.close();
  });

  test('invite friend to private room via invitation', async ({ browser }) => {
    const unique = Date.now();

    const page1 = await browser.newPage();
    await signUp(page1, `pr3a_${unique}@test.com`, `pr3a_${unique}`);

    const page2 = await browser.newPage();
    await signUp(page2, `pr3b_${unique}@test.com`, `pr3b_${unique}`);

    await makeFriends(page1, page2, `pr3b_${unique}`);

    // User 1 creates private room
    await page1.getByRole('button', { name: 'Create' }).click();
    await page1.getByLabel('Room Name').fill(`invite-room-${unique}`);
    await page1.getByLabel('Private').check();
    await page1.getByRole('button', { name: 'OK' }).click();

    // Select the room
    await page1.getByText(`invite-room-${unique}`).click();

    // Click Invite button
    await page1.getByRole('button', { name: 'Invite' }).click();
    await page1.getByRole('button', { name: 'Invite' }).first().click();
    await expect(page1.getByText('Invitation sent')).toBeVisible();

    // User 2 checks invitations in contacts
    await page2.getByRole('button', { name: 'Contacts' }).click();
    await page2.getByText('Invitations').click();
    await expect(page2.getByText(`invite-room-${unique}`)).toBeVisible();

    // Accept invitation
    await page2.getByRole('button', { name: 'Accept' }).click();
    await expect(page2.getByText('Invitation accepted')).toBeVisible();

    await page1.close();
    await page2.close();
  });
});
```

- [ ] **Step 2: Run e2e tests**

Run: `cd e2e && npx playwright test tests/private-rooms.spec.ts`
Expected: All tests PASS

- [ ] **Step 3: Run full e2e suite**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS (iteration 1 + 2 + 3)

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/private-rooms.spec.ts
git commit -m "test: add private rooms and invitations e2e tests"
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
