# Iteration 3: Private Rooms, Contacts & Personal Messaging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can create private (invite-only) rooms, manage a friends list, block users, and have direct message conversations.

**Architecture:** Three new entities (RoomInvitation, Friendship, UserBlock) plus RoomType extensions (PRIVATE, DIRECT). Four new controllers (FriendshipController, BlockController, RoomInvitationController, DirectMessageController). Extend ChatRoomService for private/direct rooms, MessageService for block checks. WebSocket `/queue/notifications` for real-time friend requests and room invitations. Frontend adds contacts panel, user search, and splits room sidebar into Rooms/DMs.

**Tech Stack:** Spring Boot 3.5.6, spring-boot-starter-websocket, STOMP, PostgreSQL 16, Liquibase, React 19, Ant Design 5, @stomp/stompjs, TypeScript

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|----------------|
| `backend/src/main/resources/db/changelog/changes/008-create-room-invitations.sql` | room_invitations table |
| `backend/src/main/resources/db/changelog/changes/009-create-friendships.sql` | friendships table |
| `backend/src/main/resources/db/changelog/changes/010-create-user-blocks.sql` | user_blocks table |
| `backend/src/main/resources/db/changelog/changes/011-relax-room-name-unique.sql` | Partial unique index on room name for PUBLIC only |
| `backend/src/main/java/com/chatapp/entity/RoomInvitation.java` | RoomInvitation JPA entity |
| `backend/src/main/java/com/chatapp/entity/Friendship.java` | Friendship JPA entity |
| `backend/src/main/java/com/chatapp/entity/UserBlock.java` | UserBlock JPA entity |
| `backend/src/main/java/com/chatapp/entity/InvitationStatus.java` | Enum: PENDING, ACCEPTED, DECLINED |
| `backend/src/main/java/com/chatapp/entity/FriendshipStatus.java` | Enum: PENDING, ACCEPTED, DECLINED |
| `backend/src/main/java/com/chatapp/repository/RoomInvitationRepository.java` | RoomInvitation queries |
| `backend/src/main/java/com/chatapp/repository/FriendshipRepository.java` | Friendship queries |
| `backend/src/main/java/com/chatapp/repository/UserBlockRepository.java` | UserBlock queries |
| `backend/src/main/java/com/chatapp/dto/InviteUserRequest.java` | Invitation request DTO |
| `backend/src/main/java/com/chatapp/dto/FriendRequestRequest.java` | Friend request DTO |
| `backend/src/main/java/com/chatapp/dto/RoomInvitationResponse.java` | Invitation response DTO |
| `backend/src/main/java/com/chatapp/dto/FriendshipResponse.java` | Friendship response DTO |
| `backend/src/main/java/com/chatapp/dto/UserBlockResponse.java` | Block response DTO |
| `backend/src/main/java/com/chatapp/dto/UserSearchResponse.java` | User search result DTO |
| `backend/src/main/java/com/chatapp/service/FriendshipService.java` | Friend request lifecycle |
| `backend/src/main/java/com/chatapp/service/BlockService.java` | Block/unblock logic |
| `backend/src/main/java/com/chatapp/service/RoomInvitationService.java` | Room invitation lifecycle |
| `backend/src/main/java/com/chatapp/controller/FriendshipController.java` | Friend REST endpoints |
| `backend/src/main/java/com/chatapp/controller/BlockController.java` | Block REST endpoints |
| `backend/src/main/java/com/chatapp/controller/RoomInvitationController.java` | Invitation REST endpoints |
| `backend/src/main/java/com/chatapp/controller/DirectMessageController.java` | DM room creation endpoint |

### Backend — Modified Files

| File | Changes |
|------|---------|
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | Include new migrations |
| `backend/src/main/java/com/chatapp/entity/RoomType.java` | Add PRIVATE, DIRECT |
| `backend/src/main/java/com/chatapp/dto/CreateRoomRequest.java` | Add optional type field |
| `backend/src/main/java/com/chatapp/repository/ChatRoomRepository.java` | Add DM room queries |
| `backend/src/main/java/com/chatapp/repository/UserRepository.java` | Add user search query |
| `backend/src/main/java/com/chatapp/service/ChatRoomService.java` | Support PRIVATE rooms, DM find-or-create |
| `backend/src/main/java/com/chatapp/service/MessageService.java` | Block check on DM send |
| `backend/src/main/java/com/chatapp/controller/UserController.java` | Add search endpoint |
| `backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java` | Handle new exceptions |

### Frontend — New Files

| File | Responsibility |
|------|----------------|
| `frontend/src/api/friends.ts` | Friends API calls |
| `frontend/src/api/blocks.ts` | Block API calls |
| `frontend/src/api/invitations.ts` | Invitation API calls |
| `frontend/src/api/directMessages.ts` | DM API calls |
| `frontend/src/components/ContactsPanel.tsx` | Contacts drawer with Friends/Requests/Blocked tabs |
| `frontend/src/components/UserSearchModal.tsx` | User search with action buttons |
| `frontend/src/components/FriendRequestList.tsx` | Incoming/outgoing friend requests |
| `frontend/src/components/InvitationList.tsx` | Pending room invitations |
| `frontend/src/components/InviteToRoomModal.tsx` | Invite friend to private room |
| `frontend/src/components/UserProfilePopover.tsx` | Username click popover with actions |

### Frontend — Modified Files

| File | Changes |
|------|---------|
| `frontend/src/api/types.ts` | Add new types |
| `frontend/src/api/users.ts` | Add searchUsers |
| `frontend/src/components/RoomList.tsx` | Split into Rooms/DMs sections, add Contacts button |
| `frontend/src/components/CreateRoomModal.tsx` | Add room type toggle |
| `frontend/src/components/RoomHeader.tsx` | Invite button for PRIVATE, DM display name |
| `frontend/src/hooks/useWebSocket.ts` | Add notification subscription |
| `frontend/src/pages/ChatLayout.tsx` | Notification handling, DM room support |

---

### Task 1: Database Migrations

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/008-create-room-invitations.sql`
- Create: `backend/src/main/resources/db/changelog/changes/009-create-friendships.sql`
- Create: `backend/src/main/resources/db/changelog/changes/010-create-user-blocks.sql`
- Create: `backend/src/main/resources/db/changelog/changes/011-relax-room-name-unique.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration 008 — room_invitations table**

Create `backend/src/main/resources/db/changelog/changes/008-create-room-invitations.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:008-create-room-invitations
CREATE TABLE room_invitations (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    inviter_id BIGINT NOT NULL REFERENCES users(id),
    invitee_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_room_invitations_pending
    ON room_invitations (room_id, invitee_id)
    WHERE status = 'PENDING';
```

- [ ] **Step 2: Create migration 009 — friendships table**

Create `backend/src/main/resources/db/changelog/changes/009-create-friendships.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:009-create-friendships
CREATE TABLE friendships (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    addressee_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT friendships_unique UNIQUE (requester_id, addressee_id)
);

CREATE INDEX idx_friendships_addressee ON friendships (addressee_id);
```

- [ ] **Step 3: Create migration 010 — user_blocks table**

Create `backend/src/main/resources/db/changelog/changes/010-create-user-blocks.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:010-create-user-blocks
CREATE TABLE user_blocks (
    id BIGSERIAL PRIMARY KEY,
    blocker_id BIGINT NOT NULL REFERENCES users(id),
    blocked_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT user_blocks_unique UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);
```

- [ ] **Step 4: Create migration 011 — relax room name uniqueness**

Create `backend/src/main/resources/db/changelog/changes/011-relax-room-name-unique.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:011-relax-room-name-unique
ALTER TABLE chat_rooms DROP CONSTRAINT chat_rooms_name_unique;

CREATE UNIQUE INDEX chat_rooms_name_unique_public
    ON chat_rooms (name)
    WHERE type = 'PUBLIC';
```

- [ ] **Step 5: Update changelog master**

Append to `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/changes/008-create-room-invitations.sql
  - include:
      file: db/changelog/changes/009-create-friendships.sql
  - include:
      file: db/changelog/changes/010-create-user-blocks.sql
  - include:
      file: db/changelog/changes/011-relax-room-name-unique.sql
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat: add room invitations, friendships, user blocks migrations and relax room name uniqueness"
```

---

### Task 2: Backend Entities and Enums

**Files:**
- Modify: `backend/src/main/java/com/chatapp/entity/RoomType.java`
- Create: `backend/src/main/java/com/chatapp/entity/InvitationStatus.java`
- Create: `backend/src/main/java/com/chatapp/entity/FriendshipStatus.java`
- Create: `backend/src/main/java/com/chatapp/entity/RoomInvitation.java`
- Create: `backend/src/main/java/com/chatapp/entity/Friendship.java`
- Create: `backend/src/main/java/com/chatapp/entity/UserBlock.java`

- [ ] **Step 1: Extend RoomType enum**

Replace `backend/src/main/java/com/chatapp/entity/RoomType.java`:

```java
package com.chatapp.entity;

public enum RoomType {
    PUBLIC,
    PRIVATE,
    DIRECT
}
```

- [ ] **Step 2: Create InvitationStatus enum**

Create `backend/src/main/java/com/chatapp/entity/InvitationStatus.java`:

```java
package com.chatapp.entity;

public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
```

- [ ] **Step 3: Create FriendshipStatus enum**

Create `backend/src/main/java/com/chatapp/entity/FriendshipStatus.java`:

```java
package com.chatapp.entity;

public enum FriendshipStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
```

- [ ] **Step 4: Create RoomInvitation entity**

Create `backend/src/main/java/com/chatapp/entity/RoomInvitation.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "room_invitations")
public class RoomInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}
```

- [ ] **Step 5: Create Friendship entity**

Create `backend/src/main/java/com/chatapp/entity/Friendship.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "friendships", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"requester_id", "addressee_id"})
})
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}
```

- [ ] **Step 6: Create UserBlock entity**

Create `backend/src/main/java/com/chatapp/entity/UserBlock.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "user_blocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"})
})
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

}
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/
git commit -m "feat: add RoomInvitation, Friendship, UserBlock entities and PRIVATE/DIRECT room types"
```

---

### Task 3: Backend Repositories

**Files:**
- Create: `backend/src/main/java/com/chatapp/repository/RoomInvitationRepository.java`
- Create: `backend/src/main/java/com/chatapp/repository/FriendshipRepository.java`
- Create: `backend/src/main/java/com/chatapp/repository/UserBlockRepository.java`
- Modify: `backend/src/main/java/com/chatapp/repository/ChatRoomRepository.java`
- Modify: `backend/src/main/java/com/chatapp/repository/UserRepository.java`

- [ ] **Step 1: Create RoomInvitationRepository**

Create `backend/src/main/java/com/chatapp/repository/RoomInvitationRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.InvitationStatus;
import com.chatapp.entity.RoomInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {

    boolean existsByRoomIdAndInviteeIdAndStatus(UUID roomId, Long inviteeId, InvitationStatus status);

    List<RoomInvitation> findByInviteeIdAndStatus(Long inviteeId, InvitationStatus status);

}
```

- [ ] **Step 2: Create FriendshipRepository**

Create `backend/src/main/java/com/chatapp/repository/FriendshipRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE " +
           "((f.requester.id = :userIdA AND f.addressee.id = :userIdB) OR " +
           "(f.requester.id = :userIdB AND f.addressee.id = :userIdA))")
    Optional<Friendship> findBetweenUsers(Long userIdA, Long userIdB);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = :status")
    List<Friendship> findByUserIdAndStatus(Long userId, FriendshipStatus status);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = 'PENDING'")
    List<Friendship> findPendingByUserId(Long userId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f WHERE " +
           "((f.requester.id = :userIdA AND f.addressee.id = :userIdB) OR " +
           "(f.requester.id = :userIdB AND f.addressee.id = :userIdA)) AND f.status = 'ACCEPTED'")
    boolean areFriends(Long userIdA, Long userIdB);

}
```

- [ ] **Step 3: Create UserBlockRepository**

Create `backend/src/main/java/com/chatapp/repository/UserBlockRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM UserBlock b WHERE " +
           "(b.blocker.id = :userIdA AND b.blocked.id = :userIdB) OR " +
           "(b.blocker.id = :userIdB AND b.blocked.id = :userIdA)")
    boolean isBlockedEitherDirection(Long userIdA, Long userIdB);

    List<UserBlock> findByBlockerId(Long blockerId);

}
```

- [ ] **Step 4: Extend ChatRoomRepository with DM queries**

Add to `backend/src/main/java/com/chatapp/repository/ChatRoomRepository.java`:

```java
    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'DIRECT' AND r.name = :name")
    Optional<ChatRoom> findDirectRoomByName(String name);

    boolean existsByNameAndType(String name, com.chatapp.entity.RoomType type);
```

Full file:

```java
package com.chatapp.repository;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndType(String name, RoomType type);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PUBLIC' AND LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<ChatRoom> searchPublicRooms(String search, Pageable pageable);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PUBLIC'")
    Page<ChatRoom> findAllPublicRooms(Pageable pageable);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'DIRECT' AND r.name = :name")
    Optional<ChatRoom> findDirectRoomByName(String name);

}
```

- [ ] **Step 5: Extend UserRepository with search query**

Add to `backend/src/main/java/com/chatapp/repository/UserRepository.java`:

```java
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND " +
           "(LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<User> searchUsers(String query, Pageable pageable);
```

Full file:

```java
package com.chatapp.repository;

import com.chatapp.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND " +
           "(LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<User> searchUsers(String query, Pageable pageable);
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/chatapp/repository/
git commit -m "feat: add RoomInvitation, Friendship, UserBlock repositories and extend ChatRoom/User repos"
```

---

### Task 4: Backend DTOs

**Files:**
- Create: `backend/src/main/java/com/chatapp/dto/InviteUserRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/FriendRequestRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/RoomInvitationResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/FriendshipResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/UserBlockResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/UserSearchResponse.java`
- Modify: `backend/src/main/java/com/chatapp/dto/CreateRoomRequest.java`

- [ ] **Step 1: Create request DTOs**

Create `backend/src/main/java/com/chatapp/dto/InviteUserRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteUserRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

}
```

Create `backend/src/main/java/com/chatapp/dto/FriendRequestRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FriendRequestRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

}
```

- [ ] **Step 2: Create response DTOs**

Create `backend/src/main/java/com/chatapp/dto/RoomInvitationResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.RoomInvitation;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RoomInvitationResponse {

    private Long id;
    private UUID roomId;
    private String roomName;
    private Long inviterId;
    private String inviterUsername;
    private String status;
    private Instant createdAt;

    public static RoomInvitationResponse fromEntity(RoomInvitation invitation) {
        RoomInvitationResponse response = new RoomInvitationResponse();
        response.setId(invitation.getId());
        response.setRoomId(invitation.getRoom().getId());
        response.setRoomName(invitation.getRoom().getName());
        response.setInviterId(invitation.getInviter().getId());
        response.setInviterUsername(invitation.getInviter().getUsername());
        response.setStatus(invitation.getStatus().name());
        response.setCreatedAt(invitation.getCreatedAt());
        return response;
    }

}
```

Create `backend/src/main/java/com/chatapp/dto/FriendshipResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.User;
import lombok.Data;

import java.time.Instant;

@Data
public class FriendshipResponse {

    private Long id;
    private Long friendUserId;
    private String friendUsername;
    private String friendDisplayName;
    private String status;
    private String direction;
    private Instant createdAt;

    public static FriendshipResponse fromEntity(Friendship friendship, Long currentUserId) {
        FriendshipResponse response = new FriendshipResponse();
        response.setId(friendship.getId());
        response.setStatus(friendship.getStatus().name());
        response.setCreatedAt(friendship.getCreatedAt());

        boolean isRequester = friendship.getRequester().getId().equals(currentUserId);
        response.setDirection(isRequester ? "OUTGOING" : "INCOMING");

        User friend = isRequester ? friendship.getAddressee() : friendship.getRequester();
        response.setFriendUserId(friend.getId());
        response.setFriendUsername(friend.getUsername());
        response.setFriendDisplayName(friend.getDisplayName());

        return response;
    }

}
```

Create `backend/src/main/java/com/chatapp/dto/UserBlockResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.UserBlock;
import lombok.Data;

import java.time.Instant;

@Data
public class UserBlockResponse {

    private Long id;
    private Long blockedUserId;
    private String blockedUsername;
    private Instant createdAt;

    public static UserBlockResponse fromEntity(UserBlock block) {
        UserBlockResponse response = new UserBlockResponse();
        response.setId(block.getId());
        response.setBlockedUserId(block.getBlocked().getId());
        response.setBlockedUsername(block.getBlocked().getUsername());
        response.setCreatedAt(block.getCreatedAt());
        return response;
    }

}
```

Create `backend/src/main/java/com/chatapp/dto/UserSearchResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.User;
import lombok.Data;

@Data
public class UserSearchResponse {

    private Long id;
    private String username;
    private String displayName;

    public static UserSearchResponse fromEntity(User user) {
        UserSearchResponse response = new UserSearchResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        return response;
    }

}
```

- [ ] **Step 3: Extend CreateRoomRequest with type field**

Replace `backend/src/main/java/com/chatapp/dto/CreateRoomRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    @Size(max = 100, message = "Room name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private String type;

}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/dto/
git commit -m "feat: add iteration 3 DTOs and extend CreateRoomRequest with type field"
```

---

### Task 5: FriendshipService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/FriendshipService.java`

- [ ] **Step 1: Create FriendshipService**

Create `backend/src/main/java/com/chatapp/service/FriendshipService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.FriendshipStatus;
import com.chatapp.entity.User;
import com.chatapp.repository.FriendshipRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Friendship sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }

        if (userBlockRepository.isBlockedEitherDirection(requesterId, addresseeId)) {
            throw new IllegalStateException("Cannot send friend request to this user");
        }

        friendshipRepository.findBetweenUsers(requesterId, addresseeId).ifPresent(existing -> {
            throw new IllegalStateException("A friend request already exists between these users");
        });

        User requester = userService.findById(requesterId);
        User addressee = userService.findById(addresseeId);

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship = friendshipRepository.save(friendship);

        messagingTemplate.convertAndSendToUser(
                addresseeId.toString(),
                "/queue/notifications",
                Map.of("type", "FRIEND_REQUEST", "data", Map.of(
                        "friendshipId", friendship.getId(),
                        "fromUserId", requesterId,
                        "fromUsername", requester.getUsername(),
                        "fromDisplayName", requester.getDisplayName()
                ))
        );

        return friendship;
    }

    @Transactional
    public Friendship acceptRequest(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalStateException("Only the addressee can accept this request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        return friendshipRepository.save(friendship);
    }

    @Transactional
    public Friendship declineRequest(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalStateException("Only the addressee can decline this request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending");
        }

        friendship.setStatus(FriendshipStatus.DECLINED);
        return friendshipRepository.save(friendship);
    }

    @Transactional
    public void removeFriend(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friendship not found"));

        if (!friendship.getRequester().getId().equals(userId) &&
            !friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalStateException("You are not part of this friendship");
        }

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Friendship is not active");
        }

        friendshipRepository.delete(friendship);
    }

    public List<Friendship> getFriends(Long userId) {
        return friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED);
    }

    public List<Friendship> getPendingRequests(Long userId) {
        return friendshipRepository.findPendingByUserId(userId);
    }

    public boolean areFriends(Long userIdA, Long userIdB) {
        return friendshipRepository.areFriends(userIdA, userIdB);
    }

}
```

- [ ] **Step 2: Verify build**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/FriendshipService.java
git commit -m "feat: add FriendshipService with request lifecycle and WebSocket notifications"
```

---

### Task 6: BlockService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/BlockService.java`

- [ ] **Step 1: Create BlockService**

Create `backend/src/main/java/com/chatapp/service/BlockService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.FriendshipStatus;
import com.chatapp.entity.User;
import com.chatapp.entity.UserBlock;
import com.chatapp.repository.FriendshipRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserService userService;

    @Transactional
    public UserBlock blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new IllegalStateException("User is already blocked");
        }

        // Remove any existing friendship
        Optional<Friendship> friendship = friendshipRepository.findBetweenUsers(blockerId, blockedId);
        friendship.ifPresent(friendshipRepository::delete);

        User blocker = userService.findById(blockerId);
        User blocked = userService.findById(blockedId);

        UserBlock block = new UserBlock();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        return userBlockRepository.save(block);
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        UserBlock block = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new IllegalArgumentException("User is not blocked"));
        userBlockRepository.delete(block);
    }

    public List<UserBlock> getBlockedUsers(Long blockerId) {
        return userBlockRepository.findByBlockerId(blockerId);
    }

    public boolean isBlocked(Long userIdA, Long userIdB) {
        return userBlockRepository.isBlockedEitherDirection(userIdA, userIdB);
    }

}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/BlockService.java
git commit -m "feat: add BlockService with block/unblock and friendship cascade"
```

---

### Task 7: RoomInvitationService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/RoomInvitationService.java`

- [ ] **Step 1: Create RoomInvitationService**

Create `backend/src/main/java/com/chatapp/service/RoomInvitationService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.InvitationStatus;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomInvitation;
import com.chatapp.entity.RoomType;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.RoomInvitationRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomInvitationService {

    private final RoomInvitationRepository invitationRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomMemberRepository memberRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public RoomInvitation inviteUser(java.util.UUID roomId, Long inviterId, Long inviteeId) {
        ChatRoom room = chatRoomService.findById(roomId);

        if (room.getType() != RoomType.PRIVATE) {
            throw new IllegalStateException("Can only invite to private rooms");
        }

        chatRoomService.validateMembership(roomId, inviterId);

        if (memberRepository.existsByRoomIdAndUserId(roomId, inviteeId)) {
            throw new IllegalStateException("User is already a member of this room");
        }

        if (invitationRepository.existsByRoomIdAndInviteeIdAndStatus(roomId, inviteeId, InvitationStatus.PENDING)) {
            throw new IllegalStateException("A pending invitation already exists for this user");
        }

        if (userBlockRepository.isBlockedEitherDirection(inviterId, inviteeId)) {
            throw new IllegalStateException("Cannot invite this user");
        }

        User inviter = userService.findById(inviterId);
        User invitee = userService.findById(inviteeId);

        RoomInvitation invitation = new RoomInvitation();
        invitation.setRoom(room);
        invitation.setInviter(inviter);
        invitation.setInvitee(invitee);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation = invitationRepository.save(invitation);

        messagingTemplate.convertAndSendToUser(
                inviteeId.toString(),
                "/queue/notifications",
                Map.of("type", "ROOM_INVITATION", "data", Map.of(
                        "invitationId", invitation.getId(),
                        "roomId", roomId.toString(),
                        "roomName", room.getName(),
                        "inviterUsername", inviter.getUsername()
                ))
        );

        return invitation;
    }

    @Transactional
    public RoomInvitation acceptInvitation(Long invitationId, Long userId) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getInvitee().getId().equals(userId)) {
            throw new IllegalStateException("Only the invitee can accept this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not pending");
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation = invitationRepository.save(invitation);

        User invitee = userService.findById(userId);
        chatRoomService.joinRoom(invitation.getRoom().getId(), invitee);

        return invitation;
    }

    @Transactional
    public RoomInvitation declineInvitation(Long invitationId, Long userId) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getInvitee().getId().equals(userId)) {
            throw new IllegalStateException("Only the invitee can decline this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not pending");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        return invitationRepository.save(invitation);
    }

    public List<RoomInvitation> getPendingInvitations(Long userId) {
        return invitationRepository.findByInviteeIdAndStatus(userId, InvitationStatus.PENDING);
    }

}
```

- [ ] **Step 2: Verify build**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/RoomInvitationService.java
git commit -m "feat: add RoomInvitationService with invite lifecycle and WebSocket notifications"
```

---

### Task 8: Extend ChatRoomService and MessageService

**Files:**
- Modify: `backend/src/main/java/com/chatapp/service/ChatRoomService.java`
- Modify: `backend/src/main/java/com/chatapp/service/MessageService.java`

- [ ] **Step 1: Extend ChatRoomService for PRIVATE and DIRECT rooms**

Replace `backend/src/main/java/com/chatapp/service/ChatRoomService.java`:

```java
package com.chatapp.service;

import com.chatapp.dto.CreateRoomRequest;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomType;
import com.chatapp.entity.User;
import com.chatapp.exception.NotRoomMemberException;
import com.chatapp.exception.RoomNameAlreadyExistsException;
import com.chatapp.exception.RoomNotFoundException;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;

    @Transactional
    public ChatRoom createRoom(CreateRoomRequest request, User owner) {
        RoomType type = RoomType.PUBLIC;
        if (request.getType() != null) {
            type = RoomType.valueOf(request.getType().toUpperCase());
        }

        if (type == RoomType.PUBLIC && roomRepository.existsByNameAndType(request.getName(), RoomType.PUBLIC)) {
            throw new RoomNameAlreadyExistsException("A public room with this name already exists");
        }

        ChatRoom room = new ChatRoom();
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setType(type);
        room.setOwner(owner);
        room = roomRepository.save(room);

        ChatRoomMember membership = new ChatRoomMember();
        membership.setRoom(room);
        membership.setUser(owner);
        membership.setRole(MemberRole.OWNER);
        memberRepository.save(membership);

        return room;
    }

    @Transactional
    public ChatRoom findOrCreateDirectRoom(User userA, User userB) {
        Long minId = Math.min(userA.getId(), userB.getId());
        Long maxId = Math.max(userA.getId(), userB.getId());
        String dmName = "dm-" + minId + "-" + maxId;

        return roomRepository.findDirectRoomByName(dmName).orElseGet(() -> {
            ChatRoom room = new ChatRoom();
            room.setName(dmName);
            room.setType(RoomType.DIRECT);
            room.setOwner(userA);
            room = roomRepository.save(room);

            ChatRoomMember memberA = new ChatRoomMember();
            memberA.setRoom(room);
            memberA.setUser(userA);
            memberA.setRole(MemberRole.MEMBER);
            memberRepository.save(memberA);

            ChatRoomMember memberB = new ChatRoomMember();
            memberB.setRoom(room);
            memberB.setUser(userB);
            memberB.setRole(MemberRole.MEMBER);
            memberRepository.save(memberB);

            return room;
        });
    }

    public ChatRoom findById(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    public List<ChatRoom> getUserRooms(Long userId) {
        return memberRepository.findRoomsByUserId(userId);
    }

    public Page<ChatRoom> getPublicRooms(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (search != null && !search.isBlank()) {
            return roomRepository.searchPublicRooms(search, pageable);
        }
        return roomRepository.findAllPublicRooms(pageable);
    }

    @Transactional
    public ChatRoomMember joinRoom(UUID roomId, User user) {
        ChatRoom room = findById(roomId);

        if (memberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            return memberRepository.findByRoomIdAndUserId(roomId, user.getId()).get();
        }

        ChatRoomMember membership = new ChatRoomMember();
        membership.setRoom(room);
        membership.setUser(user);
        membership.setRole(MemberRole.MEMBER);
        return memberRepository.save(membership);
    }

    @Transactional
    public void leaveRoom(UUID roomId, Long userId) {
        ChatRoom room = findById(roomId);

        if (room.getOwner().getId().equals(userId)) {
            throw new IllegalStateException("Room owner cannot leave the room");
        }

        ChatRoomMember membership = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotRoomMemberException("Not a member of this room"));
        memberRepository.delete(membership);
    }

    public List<ChatRoomMember> getMembers(UUID roomId) {
        findById(roomId);
        return memberRepository.findByRoomId(roomId);
    }

    public void validateMembership(UUID roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new NotRoomMemberException("Not a member of this room");
        }
    }

    public long getMemberCount(UUID roomId) {
        return memberRepository.countByRoomId(roomId);
    }

}
```

- [ ] **Step 2: Extend MessageService with block check**

Replace `backend/src/main/java/com/chatapp/service/MessageService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.Message;
import com.chatapp.entity.RoomType;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserBlockRepository userBlockRepository;
    private final ChatRoomMemberRepository memberRepository;

    @Transactional
    public Message sendMessage(ChatRoom room, User sender, String content) {
        if (room.getType() == RoomType.DIRECT) {
            List<ChatRoomMember> members = memberRepository.findByRoomId(room.getId());
            for (ChatRoomMember member : members) {
                if (!member.getUser().getId().equals(sender.getId())) {
                    if (userBlockRepository.isBlockedEitherDirection(sender.getId(), member.getUser().getId())) {
                        throw new IllegalStateException("Cannot send messages in this conversation");
                    }
                }
            }
        }

        Message message = new Message();
        message.setRoom(room);
        message.setSender(sender);
        message.setContent(content);
        return messageRepository.save(message);
    }

    public List<Message> getMessages(UUID roomId, Instant before, int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        if (before != null) {
            return messageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(roomId, before, pageable);
        }
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }

}
```

- [ ] **Step 3: Verify build**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/ChatRoomService.java backend/src/main/java/com/chatapp/service/MessageService.java
git commit -m "feat: extend ChatRoomService for private/direct rooms and MessageService with block checks"
```

---

### Task 9: Backend Controllers

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/FriendshipController.java`
- Create: `backend/src/main/java/com/chatapp/controller/BlockController.java`
- Create: `backend/src/main/java/com/chatapp/controller/RoomInvitationController.java`
- Create: `backend/src/main/java/com/chatapp/controller/DirectMessageController.java`
- Modify: `backend/src/main/java/com/chatapp/controller/UserController.java`
- Modify: `backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create FriendshipController**

Create `backend/src/main/java/com/chatapp/controller/FriendshipController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.FriendRequestRequest;
import com.chatapp.dto.FriendshipResponse;
import com.chatapp.entity.Friendship;
import com.chatapp.service.FriendshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request")
    public ResponseEntity<FriendshipResponse> sendRequest(
            @Valid @RequestBody FriendRequestRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Friendship friendship = friendshipService.sendRequest(userId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(FriendshipResponse.fromEntity(friendship, userId));
    }

    @GetMapping
    public ResponseEntity<List<FriendshipResponse>> getFriends(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<FriendshipResponse> friends = friendshipService.getFriends(userId).stream()
                .map(f -> FriendshipResponse.fromEntity(f, userId))
                .toList();
        return ResponseEntity.ok(friends);
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendshipResponse>> getPendingRequests(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<FriendshipResponse> requests = friendshipService.getPendingRequests(userId).stream()
                .map(f -> FriendshipResponse.fromEntity(f, userId))
                .toList();
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<FriendshipResponse> acceptRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Friendship friendship = friendshipService.acceptRequest(id, userId);
        return ResponseEntity.ok(FriendshipResponse.fromEntity(friendship, userId));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<FriendshipResponse> declineRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Friendship friendship = friendshipService.declineRequest(id, userId);
        return ResponseEntity.ok(FriendshipResponse.fromEntity(friendship, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFriend(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        friendshipService.removeFriend(id, userId);
        return ResponseEntity.ok().build();
    }

}
```

- [ ] **Step 2: Create BlockController**

Create `backend/src/main/java/com/chatapp/controller/BlockController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.UserBlockResponse;
import com.chatapp.entity.UserBlock;
import com.chatapp.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/api/users/{userId}/block")
    public ResponseEntity<UserBlockResponse> blockUser(
            @PathVariable Long userId,
            Authentication authentication) {
        Long blockerId = (Long) authentication.getPrincipal();
        UserBlock block = blockService.blockUser(blockerId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserBlockResponse.fromEntity(block));
    }

    @DeleteMapping("/api/users/{userId}/block")
    public ResponseEntity<Void> unblockUser(
            @PathVariable Long userId,
            Authentication authentication) {
        Long blockerId = (Long) authentication.getPrincipal();
        blockService.unblockUser(blockerId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/users/me/blocks")
    public ResponseEntity<List<UserBlockResponse>> getBlockedUsers(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserBlockResponse> blocks = blockService.getBlockedUsers(userId).stream()
                .map(UserBlockResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(blocks);
    }

}
```

- [ ] **Step 3: Create RoomInvitationController**

Create `backend/src/main/java/com/chatapp/controller/RoomInvitationController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.InviteUserRequest;
import com.chatapp.dto.RoomInvitationResponse;
import com.chatapp.entity.RoomInvitation;
import com.chatapp.service.RoomInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RoomInvitationController {

    private final RoomInvitationService invitationService;

    @PostMapping("/api/rooms/{roomId}/invitations")
    public ResponseEntity<RoomInvitationResponse> inviteUser(
            @PathVariable UUID roomId,
            @Valid @RequestBody InviteUserRequest request,
            Authentication authentication) {
        Long inviterId = (Long) authentication.getPrincipal();
        RoomInvitation invitation = invitationService.inviteUser(roomId, inviterId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomInvitationResponse.fromEntity(invitation));
    }

    @GetMapping("/api/users/me/invitations")
    public ResponseEntity<List<RoomInvitationResponse>> getPendingInvitations(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<RoomInvitationResponse> invitations = invitationService.getPendingInvitations(userId).stream()
                .map(RoomInvitationResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(invitations);
    }

    @PostMapping("/api/invitations/{id}/accept")
    public ResponseEntity<RoomInvitationResponse> acceptInvitation(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        RoomInvitation invitation = invitationService.acceptInvitation(id, userId);
        return ResponseEntity.ok(RoomInvitationResponse.fromEntity(invitation));
    }

    @PostMapping("/api/invitations/{id}/decline")
    public ResponseEntity<RoomInvitationResponse> declineInvitation(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        RoomInvitation invitation = invitationService.declineInvitation(id, userId);
        return ResponseEntity.ok(RoomInvitationResponse.fromEntity(invitation));
    }

}
```

- [ ] **Step 4: Create DirectMessageController**

Create `backend/src/main/java/com/chatapp/controller/DirectMessageController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.ChatRoomResponse;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.User;
import com.chatapp.service.BlockService;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.FriendshipService;
import com.chatapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {

    private final ChatRoomService chatRoomService;
    private final FriendshipService friendshipService;
    private final BlockService blockService;
    private final UserService userService;

    @PostMapping("/{userId}")
    public ResponseEntity<ChatRoomResponse> getOrCreateDMRoom(
            @PathVariable Long userId,
            Authentication authentication) {
        Long currentUserId = (Long) authentication.getPrincipal();

        if (currentUserId.equals(userId)) {
            throw new IllegalArgumentException("Cannot create a DM with yourself");
        }

        if (blockService.isBlocked(currentUserId, userId)) {
            throw new IllegalStateException("Cannot create a DM with this user");
        }

        if (!friendshipService.areFriends(currentUserId, userId)) {
            throw new IllegalStateException("You must be friends to start a direct message");
        }

        User currentUser = userService.findById(currentUserId);
        User otherUser = userService.findById(userId);
        ChatRoom room = chatRoomService.findOrCreateDirectRoom(currentUser, otherUser);
        long memberCount = chatRoomService.getMemberCount(room.getId());
        return ResponseEntity.ok(ChatRoomResponse.fromEntity(room, memberCount));
    }

}
```

- [ ] **Step 5: Extend UserController with search endpoint**

Add to `backend/src/main/java/com/chatapp/controller/UserController.java`, after the existing `deleteAccount` method:

```java
    @GetMapping("/search")
    public ResponseEntity<List<com.chatapp.dto.UserSearchResponse>> searchUsers(
            @RequestParam String q,
            Authentication authentication) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return ResponseEntity.ok(java.util.List.of());
        }
        java.util.List<com.chatapp.entity.User> users = userRepository.searchUsers(q.trim(),
                org.springframework.data.domain.PageRequest.of(0, 20));
        Long currentUserId = (Long) authentication.getPrincipal();
        java.util.List<com.chatapp.dto.UserSearchResponse> results = users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(com.chatapp.dto.UserSearchResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(results);
    }
```

The full `UserController` needs `UserRepository` injected. Update the class to add it:

Replace the full `UserController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.ChangePasswordRequest;
import com.chatapp.dto.DeleteAccountRequest;
import com.chatapp.dto.SignUpRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.dto.UserSearchResponse;
import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.SessionConstants;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(
            @Valid @RequestBody SignUpRequest request,
            HttpSession session) {
        User user = userService.signUp(request);
        session.setAttribute(SessionConstants.SESSION_USER_KEY, user.getId());
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                user.getEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(user));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication,
            HttpSession session) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        userService.deleteAccount(userId, request.getPassword());

        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());
        sessions.keySet().forEach(sessionRepository::deleteById);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam String q,
            Authentication authentication) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        Long currentUserId = (Long) authentication.getPrincipal();
        List<UserSearchResponse> results = userRepository.searchUsers(q.trim(), PageRequest.of(0, 20)).stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(UserSearchResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(results);
    }

}
```

- [ ] **Step 6: Add exception handlers for IllegalArgumentException and IllegalStateException**

Add to `backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java`, before the generic `Exception` handler:

```java
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
```

- [ ] **Step 7: Verify build**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/ backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java
git commit -m "feat: add Friendship, Block, Invitation, DM controllers and user search endpoint"
```

---

### Task 10: Backend Build & Verify

- [ ] **Step 1: Build the full backend**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Rebuild and start Docker Compose**

Run: `docker-compose down -v && docker-compose up -d --build`
Wait for all containers to be healthy.

- [ ] **Step 3: Verify signup and friend request flow**

```bash
# Sign up two users
curl -s -c cookies1.txt -X POST http://localhost:8080/api/users/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user1@test.com","username":"user1","password":"password123","displayName":"User One"}'

curl -s -c cookies2.txt -X POST http://localhost:8080/api/users/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user2@test.com","username":"user2","password":"password123","displayName":"User Two"}'

# Search for user2
curl -s -b cookies1.txt "http://localhost:8080/api/users/search?q=user2"

# Send friend request (need user2's ID from signup response)
curl -s -b cookies1.txt -X POST http://localhost:8080/api/friends/request \
  -H "Content-Type: application/json" \
  -d '{"userId":USER2_ID}'
```

Expected: 201 responses for signups, 200 with search results, 201 for friend request.

- [ ] **Step 4: Verify private room and DM flow**

```bash
# Accept friend request (get friendship ID from previous step)
curl -s -b cookies2.txt -X POST http://localhost:8080/api/friends/FRIENDSHIP_ID/accept

# Create DM room
curl -s -b cookies1.txt -X POST http://localhost:8080/api/direct-messages/USER2_ID

# Create private room
curl -s -b cookies1.txt -X POST http://localhost:8080/api/rooms \
  -H "Content-Type: application/json" \
  -d '{"name":"secret-room","description":"Private","type":"PRIVATE"}'
```

Expected: 200 for accept, 200 for DM room with type DIRECT, 201 for private room.

- [ ] **Step 5: Commit if fixes needed**

Only commit if changes were made:
```bash
git add -A && git commit -m "fix: address issues found during backend verification"
```

---

### Task 11: Frontend Types and API Layer

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/users.ts`
- Create: `frontend/src/api/friends.ts`
- Create: `frontend/src/api/blocks.ts`
- Create: `frontend/src/api/invitations.ts`
- Create: `frontend/src/api/directMessages.ts`

- [ ] **Step 1: Add new types to `frontend/src/api/types.ts`**

Append to `frontend/src/api/types.ts`:

```typescript

export interface RoomInvitation {
  id: number;
  roomId: string;
  roomName: string;
  inviterId: number;
  inviterUsername: string;
  status: string;
  createdAt: string;
}

export interface Friendship {
  id: number;
  friendUserId: number;
  friendUsername: string;
  friendDisplayName: string | null;
  status: string;
  direction: 'INCOMING' | 'OUTGOING';
  createdAt: string;
}

export interface UserBlockInfo {
  id: number;
  blockedUserId: number;
  blockedUsername: string;
  createdAt: string;
}

export interface UserSearchResult {
  id: number;
  username: string;
  displayName: string | null;
}

export interface FriendRequestRequest {
  userId: number;
}

export interface InviteUserRequest {
  userId: number;
}

export interface NotificationPayload {
  type: 'FRIEND_REQUEST' | 'ROOM_INVITATION';
  data: Record<string, unknown>;
}
```

- [ ] **Step 2: Add searchUsers to `frontend/src/api/users.ts`**

Replace `frontend/src/api/users.ts`:

```typescript
import client from './client.ts';
import type { ChangePasswordRequest, DeleteAccountRequest, SessionInfo, UserSearchResult } from './types.ts';

export const usersApi = {
  changePassword: async (data: ChangePasswordRequest): Promise<void> => {
    await client.put('/users/me/password', data);
  },

  deleteAccount: async (data: DeleteAccountRequest): Promise<void> => {
    await client.delete('/users/me', { data });
  },

  getSessions: async (): Promise<SessionInfo[]> => {
    const response = await client.get<SessionInfo[]>('/users/me/sessions');
    return response.data;
  },

  terminateSession: async (sessionId: string): Promise<void> => {
    await client.post(`/users/me/sessions/${sessionId}/invalidate`);
  },

  searchUsers: async (query: string): Promise<UserSearchResult[]> => {
    const { data } = await client.get<UserSearchResult[]>(`/users/search?q=${encodeURIComponent(query)}`);
    return data;
  },
};
```

- [ ] **Step 3: Create friends API module**

Create `frontend/src/api/friends.ts`:

```typescript
import client from './client.ts';
import type { Friendship } from './types.ts';

export const friendsApi = {
  sendRequest: async (userId: number): Promise<Friendship> => {
    const { data } = await client.post<Friendship>('/friends/request', { userId });
    return data;
  },

  getFriends: async (): Promise<Friendship[]> => {
    const { data } = await client.get<Friendship[]>('/friends');
    return data;
  },

  getPendingRequests: async (): Promise<Friendship[]> => {
    const { data } = await client.get<Friendship[]>('/friends/requests');
    return data;
  },

  acceptRequest: async (id: number): Promise<Friendship> => {
    const { data } = await client.post<Friendship>(`/friends/${id}/accept`);
    return data;
  },

  declineRequest: async (id: number): Promise<Friendship> => {
    const { data } = await client.post<Friendship>(`/friends/${id}/decline`);
    return data;
  },

  removeFriend: async (id: number): Promise<void> => {
    await client.delete(`/friends/${id}`);
  },
};
```

- [ ] **Step 4: Create blocks API module**

Create `frontend/src/api/blocks.ts`:

```typescript
import client from './client.ts';
import type { UserBlockInfo } from './types.ts';

export const blocksApi = {
  blockUser: async (userId: number): Promise<UserBlockInfo> => {
    const { data } = await client.post<UserBlockInfo>(`/users/${userId}/block`);
    return data;
  },

  unblockUser: async (userId: number): Promise<void> => {
    await client.delete(`/users/${userId}/block`);
  },

  getBlockedUsers: async (): Promise<UserBlockInfo[]> => {
    const { data } = await client.get<UserBlockInfo[]>('/users/me/blocks');
    return data;
  },
};
```

- [ ] **Step 5: Create invitations API module**

Create `frontend/src/api/invitations.ts`:

```typescript
import client from './client.ts';
import type { RoomInvitation } from './types.ts';

export const invitationsApi = {
  inviteToRoom: async (roomId: string, userId: number): Promise<RoomInvitation> => {
    const { data } = await client.post<RoomInvitation>(`/rooms/${roomId}/invitations`, { userId });
    return data;
  },

  getMyInvitations: async (): Promise<RoomInvitation[]> => {
    const { data } = await client.get<RoomInvitation[]>('/users/me/invitations');
    return data;
  },

  acceptInvitation: async (id: number): Promise<RoomInvitation> => {
    const { data } = await client.post<RoomInvitation>(`/invitations/${id}/accept`);
    return data;
  },

  declineInvitation: async (id: number): Promise<RoomInvitation> => {
    const { data } = await client.post<RoomInvitation>(`/invitations/${id}/decline`);
    return data;
  },
};
```

- [ ] **Step 6: Create directMessages API module**

Create `frontend/src/api/directMessages.ts`:

```typescript
import client from './client.ts';
import type { ChatRoom } from './types.ts';

export const directMessagesApi = {
  getOrCreateDMRoom: async (userId: number): Promise<ChatRoom> => {
    const { data } = await client.post<ChatRoom>(`/direct-messages/${userId}`);
    return data;
  },
};
```

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/
git commit -m "feat: add friends, blocks, invitations, direct messages API modules and new types"
```

---

### Task 12: Frontend Components — ContactsPanel, UserSearch, FriendRequests

**Files:**
- Create: `frontend/src/components/ContactsPanel.tsx`
- Create: `frontend/src/components/UserSearchModal.tsx`
- Create: `frontend/src/components/FriendRequestList.tsx`
- Create: `frontend/src/components/InvitationList.tsx`
- Create: `frontend/src/components/InviteToRoomModal.tsx`
- Create: `frontend/src/components/UserProfilePopover.tsx`

- [ ] **Step 1: Create FriendRequestList**

Create `frontend/src/components/FriendRequestList.tsx`:

```tsx
import { Button, List, Typography, Tag } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import type { Friendship } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  requests: Friendship[];
  onAccept: (id: number) => void;
  onDecline: (id: number) => void;
}

export const FriendRequestList = ({ requests, onAccept, onDecline }: Props) => {
  const incoming = requests.filter(r => r.direction === 'INCOMING');
  const outgoing = requests.filter(r => r.direction === 'OUTGOING');

  return (
    <div>
      {incoming.length > 0 && (
        <>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>Incoming</Text>
          <List
            dataSource={incoming}
            renderItem={(req) => (
              <List.Item
                actions={[
                  <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => onAccept(req.id)}>Accept</Button>,
                  <Button size="small" icon={<CloseOutlined />} onClick={() => onDecline(req.id)}>Decline</Button>,
                ]}
              >
                <List.Item.Meta
                  title={req.friendUsername}
                  description={req.friendDisplayName}
                />
              </List.Item>
            )}
          />
        </>
      )}
      {outgoing.length > 0 && (
        <>
          <Text strong style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>Outgoing</Text>
          <List
            dataSource={outgoing}
            renderItem={(req) => (
              <List.Item>
                <List.Item.Meta
                  title={req.friendUsername}
                  description={req.friendDisplayName}
                />
                <Tag>Pending</Tag>
              </List.Item>
            )}
          />
        </>
      )}
      {incoming.length === 0 && outgoing.length === 0 && (
        <Text type="secondary">No pending requests</Text>
      )}
    </div>
  );
};
```

- [ ] **Step 2: Create InvitationList**

Create `frontend/src/components/InvitationList.tsx`:

```tsx
import { Button, List, Typography } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import type { RoomInvitation } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  invitations: RoomInvitation[];
  onAccept: (id: number) => void;
  onDecline: (id: number) => void;
}

export const InvitationList = ({ invitations, onAccept, onDecline }: Props) => {
  if (invitations.length === 0) {
    return <Text type="secondary">No pending invitations</Text>;
  }

  return (
    <List
      dataSource={invitations}
      renderItem={(inv) => (
        <List.Item
          actions={[
            <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => onAccept(inv.id)}>Accept</Button>,
            <Button size="small" icon={<CloseOutlined />} onClick={() => onDecline(inv.id)}>Decline</Button>,
          ]}
        >
          <List.Item.Meta
            title={`#${inv.roomName}`}
            description={`Invited by ${inv.inviterUsername}`}
          />
        </List.Item>
      )}
    />
  );
};
```

- [ ] **Step 3: Create ContactsPanel**

Create `frontend/src/components/ContactsPanel.tsx`:

```tsx
import { useState, useEffect } from 'react';
import { Drawer, Tabs, List, Button, Typography, message } from 'antd';
import { MessageOutlined, DeleteOutlined, StopOutlined } from '@ant-design/icons';
import { friendsApi } from '../api/friends.ts';
import { blocksApi } from '../api/blocks.ts';
import { invitationsApi } from '../api/invitations.ts';
import { FriendRequestList } from './FriendRequestList.tsx';
import { InvitationList } from './InvitationList.tsx';
import type { Friendship, UserBlockInfo, RoomInvitation, ChatRoom } from '../api/types.ts';
import { directMessagesApi } from '../api/directMessages.ts';

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onDMCreated: (room: ChatRoom) => void;
}

export const ContactsPanel = ({ open, onClose, onDMCreated }: Props) => {
  const [friends, setFriends] = useState<Friendship[]>([]);
  const [requests, setRequests] = useState<Friendship[]>([]);
  const [blocked, setBlocked] = useState<UserBlockInfo[]>([]);
  const [invitations, setInvitations] = useState<RoomInvitation[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [f, r, b, i] = await Promise.all([
        friendsApi.getFriends(),
        friendsApi.getPendingRequests(),
        blocksApi.getBlockedUsers(),
        invitationsApi.getMyInvitations(),
      ]);
      setFriends(f);
      setRequests(r);
      setBlocked(b);
      setInvitations(i);
    } catch {
      message.error('Failed to load contacts');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) fetchData();
  }, [open]);

  const handleAcceptFriend = async (id: number) => {
    await friendsApi.acceptRequest(id);
    message.success('Friend request accepted');
    fetchData();
  };

  const handleDeclineFriend = async (id: number) => {
    await friendsApi.declineRequest(id);
    message.success('Friend request declined');
    fetchData();
  };

  const handleRemoveFriend = async (id: number) => {
    await friendsApi.removeFriend(id);
    message.success('Friend removed');
    fetchData();
  };

  const handleUnblock = async (userId: number) => {
    await blocksApi.unblockUser(userId);
    message.success('User unblocked');
    fetchData();
  };

  const handleAcceptInvitation = async (id: number) => {
    await invitationsApi.acceptInvitation(id);
    message.success('Invitation accepted');
    fetchData();
  };

  const handleDeclineInvitation = async (id: number) => {
    await invitationsApi.declineInvitation(id);
    message.success('Invitation declined');
    fetchData();
  };

  const handleSendDM = async (userId: number) => {
    try {
      const room = await directMessagesApi.getOrCreateDMRoom(userId);
      onDMCreated(room);
      onClose();
    } catch {
      message.error('Failed to create DM');
    }
  };

  const items = [
    {
      key: 'friends',
      label: `Friends (${friends.length})`,
      children: (
        <List
          loading={loading}
          dataSource={friends}
          renderItem={(friend) => (
            <List.Item
              actions={[
                <Button size="small" icon={<MessageOutlined />} onClick={() => handleSendDM(friend.friendUserId)}>DM</Button>,
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleRemoveFriend(friend.id)}>Remove</Button>,
              ]}
            >
              <List.Item.Meta
                title={friend.friendUsername}
                description={friend.friendDisplayName}
              />
            </List.Item>
          )}
          locale={{ emptyText: 'No friends yet' }}
        />
      ),
    },
    {
      key: 'requests',
      label: `Requests (${requests.length})`,
      children: <FriendRequestList requests={requests} onAccept={handleAcceptFriend} onDecline={handleDeclineFriend} />,
    },
    {
      key: 'invitations',
      label: `Invitations (${invitations.length})`,
      children: <InvitationList invitations={invitations} onAccept={handleAcceptInvitation} onDecline={handleDeclineInvitation} />,
    },
    {
      key: 'blocked',
      label: `Blocked (${blocked.length})`,
      children: (
        <List
          loading={loading}
          dataSource={blocked}
          renderItem={(block) => (
            <List.Item
              actions={[
                <Button size="small" onClick={() => handleUnblock(block.blockedUserId)}>Unblock</Button>,
              ]}
            >
              <List.Item.Meta title={block.blockedUsername} />
            </List.Item>
          )}
          locale={{ emptyText: 'No blocked users' }}
        />
      ),
    },
  ];

  return (
    <Drawer title="Contacts" open={open} onClose={onClose} width={400}>
      <Tabs items={items} />
    </Drawer>
  );
};
```

- [ ] **Step 4: Create UserSearchModal**

Create `frontend/src/components/UserSearchModal.tsx`:

```tsx
import { useState, useEffect } from 'react';
import { Modal, Input, List, Button, Typography, message } from 'antd';
import { UserAddOutlined, MessageOutlined } from '@ant-design/icons';
import { usersApi } from '../api/users.ts';
import { friendsApi } from '../api/friends.ts';
import { directMessagesApi } from '../api/directMessages.ts';
import type { UserSearchResult, ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onDMCreated: (room: ChatRoom) => void;
}

export const UserSearchModal = ({ open, onClose, onDMCreated }: Props) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      return;
    }
  }, [open]);

  useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await usersApi.searchUsers(query);
        setResults(data);
      } catch {
        message.error('Search failed');
      } finally {
        setLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [query]);

  const handleAddFriend = async (userId: number) => {
    try {
      await friendsApi.sendRequest(userId);
      message.success('Friend request sent');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to send request');
    }
  };

  const handleSendDM = async (userId: number) => {
    try {
      const room = await directMessagesApi.getOrCreateDMRoom(userId);
      onDMCreated(room);
      onClose();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create DM');
    }
  };

  return (
    <Modal title="Search Users" open={open} onCancel={onClose} footer={null} width={500}>
      <Input.Search
        placeholder="Search by username or display name..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        style={{ marginBottom: 16 }}
        allowClear
      />
      <List
        loading={loading}
        dataSource={results}
        renderItem={(user) => (
          <List.Item
            actions={[
              <Button size="small" icon={<UserAddOutlined />} onClick={() => handleAddFriend(user.id)}>Add Friend</Button>,
              <Button size="small" icon={<MessageOutlined />} onClick={() => handleSendDM(user.id)}>DM</Button>,
            ]}
          >
            <List.Item.Meta
              title={user.username}
              description={user.displayName}
            />
          </List.Item>
        )}
        locale={{ emptyText: query.length >= 2 ? 'No users found' : 'Type at least 2 characters' }}
      />
    </Modal>
  );
};
```

- [ ] **Step 5: Create InviteToRoomModal**

Create `frontend/src/components/InviteToRoomModal.tsx`:

```tsx
import { useState, useEffect } from 'react';
import { Modal, List, Button, Typography, message } from 'antd';
import { friendsApi } from '../api/friends.ts';
import { invitationsApi } from '../api/invitations.ts';
import type { Friendship } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  open: boolean;
  roomId: string;
  onClose: () => void;
}

export const InviteToRoomModal = ({ open, roomId, onClose }: Props) => {
  const [friends, setFriends] = useState<Friendship[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      setLoading(true);
      friendsApi.getFriends()
        .then(setFriends)
        .catch(() => message.error('Failed to load friends'))
        .finally(() => setLoading(false));
    }
  }, [open]);

  const handleInvite = async (userId: number) => {
    try {
      await invitationsApi.inviteToRoom(roomId, userId);
      message.success('Invitation sent');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to invite');
    }
  };

  return (
    <Modal title="Invite to Room" open={open} onCancel={onClose} footer={null}>
      <List
        loading={loading}
        dataSource={friends}
        renderItem={(friend) => (
          <List.Item
            actions={[
              <Button size="small" type="primary" onClick={() => handleInvite(friend.friendUserId)}>Invite</Button>,
            ]}
          >
            <List.Item.Meta
              title={friend.friendUsername}
              description={friend.friendDisplayName}
            />
          </List.Item>
        )}
        locale={{ emptyText: 'No friends to invite' }}
      />
    </Modal>
  );
};
```

- [ ] **Step 6: Create UserProfilePopover**

Create `frontend/src/components/UserProfilePopover.tsx`:

```tsx
import { Popover, Button, Space, Typography, message } from 'antd';
import { UserAddOutlined, MessageOutlined, StopOutlined } from '@ant-design/icons';
import { friendsApi } from '../api/friends.ts';
import { blocksApi } from '../api/blocks.ts';
import { directMessagesApi } from '../api/directMessages.ts';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  userId: number;
  username: string;
  displayName: string | null;
  currentUserId: number;
  children: React.ReactNode;
  onDMCreated?: (room: ChatRoom) => void;
}

export const UserProfilePopover = ({ userId, username, displayName, currentUserId, children, onDMCreated }: Props) => {
  if (userId === currentUserId) {
    return <>{children}</>;
  }

  const handleAddFriend = async () => {
    try {
      await friendsApi.sendRequest(userId);
      message.success('Friend request sent');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to send request');
    }
  };

  const handleBlock = async () => {
    try {
      await blocksApi.blockUser(userId);
      message.success('User blocked');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to block user');
    }
  };

  const handleDM = async () => {
    try {
      const room = await directMessagesApi.getOrCreateDMRoom(userId);
      onDMCreated?.(room);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create DM');
    }
  };

  const content = (
    <div style={{ minWidth: 200 }}>
      <div style={{ marginBottom: 12 }}>
        <Text strong style={{ display: 'block' }}>{displayName || username}</Text>
        <Text type="secondary">@{username}</Text>
      </div>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Button size="small" icon={<UserAddOutlined />} onClick={handleAddFriend} block>Add Friend</Button>
        <Button size="small" icon={<MessageOutlined />} onClick={handleDM} block>Send DM</Button>
        <Button size="small" icon={<StopOutlined />} onClick={handleBlock} danger block>Block</Button>
      </Space>
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomLeft">
      <span style={{ cursor: 'pointer' }}>{children}</span>
    </Popover>
  );
};
```

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/ContactsPanel.tsx frontend/src/components/UserSearchModal.tsx frontend/src/components/FriendRequestList.tsx frontend/src/components/InvitationList.tsx frontend/src/components/InviteToRoomModal.tsx frontend/src/components/UserProfilePopover.tsx
git commit -m "feat: add contacts panel, user search, friend requests, invitations, and user profile components"
```

---

### Task 13: Frontend — Update Existing Components

**Files:**
- Modify: `frontend/src/components/RoomList.tsx`
- Modify: `frontend/src/components/CreateRoomModal.tsx`
- Modify: `frontend/src/components/RoomHeader.tsx`
- Modify: `frontend/src/pages/ChatLayout.tsx`

- [ ] **Step 1: Update RoomList with Rooms/DMs split and Contacts button**

Replace `frontend/src/components/RoomList.tsx`:

```tsx
import { Button, Typography } from 'antd';
import { PlusOutlined, SearchOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface RoomListProps {
  rooms: ChatRoom[];
  selectedRoomId: string | null;
  onSelectRoom: (room: ChatRoom) => void;
  onBrowse: () => void;
  onCreate: () => void;
  onContacts: () => void;
  onUserSearch: () => void;
}

export const RoomList = ({ rooms, selectedRoomId, onSelectRoom, onBrowse, onCreate, onContacts, onUserSearch }: RoomListProps) => {
  const chatRooms = rooms.filter(r => r.type !== 'DIRECT');
  const dmRooms = rooms.filter(r => r.type === 'DIRECT');

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      borderRight: '1px solid #f0f0f0',
    }}>
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0' }}>
        <Text strong style={{ fontSize: 16 }}>Rooms</Text>
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {chatRooms.map((room) => (
          <div
            key={room.id}
            onClick={() => onSelectRoom(room)}
            style={{
              padding: '10px 16px',
              cursor: 'pointer',
              background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
              borderBottom: '1px solid #f5f5f5',
            }}
          >
            <Text>{room.type === 'PRIVATE' ? '🔒' : '#'}{room.name}</Text>
          </div>
        ))}
        {dmRooms.length > 0 && (
          <>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', borderTop: '1px solid #f0f0f0' }}>
              <Text strong style={{ fontSize: 14 }}>Direct Messages</Text>
            </div>
            {dmRooms.map((room) => (
              <div
                key={room.id}
                onClick={() => onSelectRoom(room)}
                style={{
                  padding: '10px 16px',
                  cursor: 'pointer',
                  background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
                  borderBottom: '1px solid #f5f5f5',
                }}
              >
                <Text><UserOutlined style={{ marginRight: 6 }} />{room.name.replace(/^dm-\d+-\d+$/, 'Direct Message')}</Text>
              </div>
            ))}
          </>
        )}
      </div>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        padding: '8px 16px',
        borderTop: '1px solid #f0f0f0',
      }}>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<SearchOutlined />} onClick={onBrowse} style={{ flex: 1 }}>Browse</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={onCreate} style={{ flex: 1 }}>Create</Button>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<TeamOutlined />} onClick={onContacts} style={{ flex: 1 }}>Contacts</Button>
          <Button icon={<UserOutlined />} onClick={onUserSearch} style={{ flex: 1 }}>Find Users</Button>
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Update CreateRoomModal with type toggle**

Replace `frontend/src/components/CreateRoomModal.tsx`:

```tsx
import { useState } from 'react';
import { Modal, Form, Input, Radio, message } from 'antd';
import { roomsApi } from '../api/rooms.ts';
import type { ChatRoom } from '../api/types.ts';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (room: ChatRoom) => void;
}

export const CreateRoomModal = ({ open, onClose, onCreated }: Props) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const room = await roomsApi.createRoom(values);
      message.success('Room created');
      form.resetFields();
      onCreated(room);
      onClose();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create room');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal title="Create Room" open={open} onOk={handleOk} onCancel={onClose} confirmLoading={loading}>
      <Form form={form} layout="vertical" initialValues={{ type: 'PUBLIC' }}>
        <Form.Item name="name" label="Room Name" rules={[{ required: true, message: 'Room name is required' }]}>
          <Input placeholder="e.g. general" />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea placeholder="What is this room about?" rows={3} />
        </Form.Item>
        <Form.Item name="type" label="Type">
          <Radio.Group>
            <Radio value="PUBLIC">Public</Radio>
            <Radio value="PRIVATE">Private (invite-only)</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>
    </Modal>
  );
};
```

- [ ] **Step 3: Update RoomHeader with Invite button and DM display**

Replace `frontend/src/components/RoomHeader.tsx`:

```tsx
import { Button, Typography } from 'antd';
import { LogoutOutlined, UserAddOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';

const { Text, Title } = Typography;

interface Props {
  room: ChatRoom;
  currentUserId: number;
  onLeave: () => void;
  onInvite?: () => void;
}

export const RoomHeader = ({ room, currentUserId, onLeave, onInvite }: Props) => {
  const isOwner = room.ownerId === currentUserId;
  const isDirect = room.type === 'DIRECT';
  const isPrivate = room.type === 'PRIVATE';

  const displayName = isDirect
    ? 'Direct Message'
    : `${isPrivate ? '🔒 ' : '#'}${room.name}`;

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '12px 16px',
      borderBottom: '1px solid #f0f0f0',
    }}>
      <div>
        <Title level={5} style={{ margin: 0 }}>{displayName}</Title>
        {room.description && <Text type="secondary">{room.description}</Text>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Text type="secondary">{room.memberCount} members</Text>
        {isPrivate && onInvite && (
          <Button size="small" icon={<UserAddOutlined />} onClick={onInvite}>Invite</Button>
        )}
        {!isOwner && !isDirect && (
          <Button size="small" icon={<LogoutOutlined />} onClick={onLeave}>Leave</Button>
        )}
      </div>
    </div>
  );
};
```

- [ ] **Step 4: Update ChatLayout with new state and modals**

Replace `frontend/src/pages/ChatLayout.tsx`:

```tsx
import { useState, useEffect, useCallback } from 'react';
import type { ChatRoom, ChatMessage } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { useAuth } from '../contexts/AuthContext.tsx';
import { useWebSocket } from '../hooks/useWebSocket.ts';
import { AppHeader } from '../components/AppHeader.tsx';
import { RoomList } from '../components/RoomList.tsx';
import { RoomBrowser } from '../components/RoomBrowser.tsx';
import { CreateRoomModal } from '../components/CreateRoomModal.tsx';
import { ChatArea } from '../components/ChatArea.tsx';
import { MessageInput } from '../components/MessageInput.tsx';
import { RoomHeader } from '../components/RoomHeader.tsx';
import { ContactsPanel } from '../components/ContactsPanel.tsx';
import { UserSearchModal } from '../components/UserSearchModal.tsx';
import { InviteToRoomModal } from '../components/InviteToRoomModal.tsx';

export function ChatLayout() {
  const { user } = useAuth();
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [selectedRoom, setSelectedRoom] = useState<ChatRoom | null>(null);
  const [messages, setMessages] = useState<Map<string, ChatMessage[]>>(new Map());
  const [browserOpen, setBrowserOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [contactsOpen, setContactsOpen] = useState(false);
  const [userSearchOpen, setUserSearchOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);

  const handleNewMessage = useCallback((roomId: string, msg: ChatMessage) => {
    setMessages(prev => {
      const next = new Map(prev);
      const existing = next.get(roomId) || [];
      if (existing.some(m => m.id === msg.id)) return prev;
      next.set(roomId, [...existing, msg]);
      return next;
    });
  }, []);

  const { subscribe, unsubscribe, sendMessage } = useWebSocket(handleNewMessage);

  useEffect(() => {
    roomsApi.getMyRooms().then(setRooms).catch(console.error);
  }, []);

  const handleSelectRoom = useCallback(async (room: ChatRoom) => {
    if (selectedRoom) {
      unsubscribe(selectedRoom.id);
    }
    setLoadingMessages(true);
    setSelectedRoom(room);

    try {
      const msgs = await roomsApi.getMessages(room.id);
      setMessages(prev => {
        const next = new Map(prev);
        next.set(room.id, msgs.reverse());
        return next;
      });
    } catch (err) {
      console.error('Failed to load messages', err);
    } finally {
      setLoadingMessages(false);
    }

    subscribe(room.id);
  }, [selectedRoom, subscribe, unsubscribe]);

  const handleLoadMore = useCallback(async (): Promise<boolean> => {
    if (!selectedRoom) return false;
    const currentMessages = messages.get(selectedRoom.id) || [];
    const oldest = currentMessages[0];
    if (!oldest) return false;

    const olderMessages = await roomsApi.getMessages(selectedRoom.id, oldest.createdAt);
    if (olderMessages.length === 0) return false;

    setMessages(prev => {
      const next = new Map(prev);
      const existing = next.get(selectedRoom.id) || [];
      next.set(selectedRoom.id, [...olderMessages.reverse(), ...existing]);
      return next;
    });

    return olderMessages.length >= 50;
  }, [selectedRoom, messages]);

  const handleSendMessage = useCallback(async (content: string) => {
    if (!selectedRoom) return;
    try {
      const msg = await roomsApi.sendMessage(selectedRoom.id, content);
      handleNewMessage(selectedRoom.id, msg);
    } catch {
      sendMessage(selectedRoom.id, content);
    }
  }, [selectedRoom, sendMessage, handleNewMessage]);

  const handleLeaveRoom = useCallback(async () => {
    if (!selectedRoom) return;
    await roomsApi.leaveRoom(selectedRoom.id);
    unsubscribe(selectedRoom.id);
    setRooms(prev => prev.filter(r => r.id !== selectedRoom.id));
    setSelectedRoom(null);
  }, [selectedRoom, unsubscribe]);

  const handleRoomCreated = useCallback((room: ChatRoom) => {
    setRooms(prev => [...prev, room]);
    setCreateOpen(false);
    handleSelectRoom(room);
  }, [handleSelectRoom]);

  const handleRoomJoined = useCallback((room: ChatRoom) => {
    setRooms(prev => prev.some(r => r.id === room.id) ? prev : [...prev, room]);
    setBrowserOpen(false);
    handleSelectRoom(room);
  }, [handleSelectRoom]);

  const handleDMCreated = useCallback((room: ChatRoom) => {
    setRooms(prev => prev.some(r => r.id === room.id) ? prev : [...prev, room]);
    handleSelectRoom(room);
  }, [handleSelectRoom]);

  const currentMessages = selectedRoom ? (messages.get(selectedRoom.id) || []) : [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Chat Area - center */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          {selectedRoom ? (
            <>
              <RoomHeader
                room={selectedRoom}
                currentUserId={user!.id}
                onLeave={handleLeaveRoom}
                onInvite={selectedRoom.type === 'PRIVATE' ? () => setInviteOpen(true) : undefined}
              />
              <ChatArea messages={currentMessages} currentUserId={user!.id} onLoadMore={handleLoadMore} loading={loadingMessages} />
              <MessageInput onSend={handleSendMessage} />
            </>
          ) : (
            <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', color: '#999' }}>
              Select a room to start chatting
            </div>
          )}
        </div>
        {/* Right Sidebar */}
        <div style={{ width: 280, borderLeft: '1px solid #f0f0f0', backgroundColor: '#fafafa' }}>
          <RoomList
            rooms={rooms}
            selectedRoomId={selectedRoom?.id || null}
            onSelectRoom={handleSelectRoom}
            onBrowse={() => setBrowserOpen(true)}
            onCreate={() => setCreateOpen(true)}
            onContacts={() => setContactsOpen(true)}
            onUserSearch={() => setUserSearchOpen(true)}
          />
        </div>
      </div>
      <RoomBrowser open={browserOpen} onClose={() => setBrowserOpen(false)} onJoined={handleRoomJoined} joinedRoomIds={new Set(rooms.map(r => r.id))} />
      <CreateRoomModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={handleRoomCreated} />
      <ContactsPanel open={contactsOpen} onClose={() => setContactsOpen(false)} onDMCreated={handleDMCreated} />
      <UserSearchModal open={userSearchOpen} onClose={() => setUserSearchOpen(false)} onDMCreated={handleDMCreated} />
      {selectedRoom && (
        <InviteToRoomModal open={inviteOpen} roomId={selectedRoom.id} onClose={() => setInviteOpen(false)} />
      )}
    </div>
  );
}
```

- [ ] **Step 5: Update CreateRoomRequest type in `frontend/src/api/types.ts`**

In `frontend/src/api/types.ts`, update the `CreateRoomRequest` interface:

Replace:
```typescript
export interface CreateRoomRequest {
  name: string;
  description?: string;
}
```

With:
```typescript
export interface CreateRoomRequest {
  name: string;
  description?: string;
  type?: string;
}
```

- [ ] **Step 6: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/ frontend/src/pages/ChatLayout.tsx frontend/src/api/types.ts
git commit -m "feat: update RoomList, CreateRoomModal, RoomHeader, ChatLayout for private rooms and contacts"
```

---

### Task 14: End-to-End Verification

- [ ] **Step 1: Rebuild and start all services**

```bash
docker-compose down -v && docker-compose up -d --build
```

Wait for all containers to be healthy.

- [ ] **Step 2: Verify full flow in browser**

Open `http://localhost:5173` and test:

1. **Sign up two users** in separate tabs/browsers
2. **User search** — click "Find Users", search for the other user
3. **Friend request** — click "Add Friend" on search result
4. **Accept request** — other user clicks "Contacts" → Requests → Accept
5. **Send DM** — click "DM" button on friend in contacts → should create DM room
6. **Create private room** — click Create, select Private type
7. **Invite friend** — in private room, click Invite → select friend → send invitation
8. **Accept invitation** — other user sees invitation in Contacts → Invitations → Accept
9. **Block user** — click username in chat → Block → verify DM messages are rejected

- [ ] **Step 3: Fix any issues found**

If any issues are found, fix them and commit:

```bash
git add -A && git commit -m "fix: address issues found during e2e verification"
```

---

## Running the App

```bash
docker-compose up --build
```

Then open `http://localhost:5173` — sign in, search users, add friends, create private rooms, send DMs.
