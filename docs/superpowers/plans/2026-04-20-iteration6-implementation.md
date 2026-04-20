# Iteration 6: Moderation & Administration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Room owners and admins can moderate rooms — manage member roles, ban/kick users, edit room settings, and delete rooms.

**Architecture:** New `RoomBan` entity, `ADMIN` added to `MemberRole` enum. New `ModerationService` for permission-checked moderation actions. New `ModerationController` for REST endpoints. Extend `ChatRoomService` for room update/delete, extend `MessageService` for admin deletion. Frontend adds `ManageRoomModal` with tabs and extends existing components with admin actions. WebSocket notifications for ban/kick/delete events.

**Tech Stack:** Spring Boot 3.5.6, Spring Data JPA, PostgreSQL 16, Liquibase, STOMP WebSocket, React 19, Ant Design 5, TypeScript

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|----------------|
| `backend/src/main/resources/db/changelog/changes/015-create-room-bans.sql` | room_bans table |
| `backend/src/main/resources/db/changelog/changes/016-add-cascade-deletes.sql` | ON DELETE CASCADE for room-related FKs |
| `backend/src/main/java/com/chatapp/entity/RoomBan.java` | RoomBan JPA entity |
| `backend/src/main/java/com/chatapp/repository/RoomBanRepository.java` | RoomBan queries |
| `backend/src/main/java/com/chatapp/dto/BanUserRequest.java` | Ban request DTO |
| `backend/src/main/java/com/chatapp/dto/ChangeRoleRequest.java` | Role change request DTO |
| `backend/src/main/java/com/chatapp/dto/UpdateRoomRequest.java` | Room settings update DTO |
| `backend/src/main/java/com/chatapp/dto/RoomBanResponse.java` | Ban response DTO |
| `backend/src/main/java/com/chatapp/service/ModerationService.java` | Ban/kick/role management with permission checks |
| `backend/src/main/java/com/chatapp/controller/ModerationController.java` | Moderation REST endpoints |

### Backend — Modified Files

| File | Changes |
|------|---------|
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | Include migrations 015, 016 |
| `backend/src/main/java/com/chatapp/entity/MemberRole.java` | Add ADMIN value |
| `backend/src/main/java/com/chatapp/service/ChatRoomService.java` | Add updateRoom, deleteRoom, ban check on join |
| `backend/src/main/java/com/chatapp/service/MessageService.java` | Allow ADMIN role deletion |
| `backend/src/main/java/com/chatapp/controller/ChatRoomController.java` | Add PUT, DELETE room, DELETE member endpoints |
| `backend/src/main/java/com/chatapp/repository/AttachmentRepository.java` | Add findByMessageRoomId query |

### Frontend — New Files

| File | Responsibility |
|------|----------------|
| `frontend/src/api/moderation.ts` | Moderation API calls |
| `frontend/src/components/ManageRoomModal.tsx` | Tabbed modal for room management |

### Frontend — Modified Files

| File | Changes |
|------|---------|
| `frontend/src/api/types.ts` | Add RoomBan, BanUserRequest, UpdateRoomRequest, ChangeRoleRequest, new event types |
| `frontend/src/api/rooms.ts` | Add updateRoom, deleteRoom |
| `frontend/src/components/RoomHeader.tsx` | Add "Manage" button for OWNER/ADMIN |
| `frontend/src/components/MessageBubble.tsx` | Allow ADMIN delete action |
| `frontend/src/hooks/useWebSocket.ts` | Subscribe to /queue/notifications for ban events |
| `frontend/src/pages/ChatLayout.tsx` | Handle ROOM_BANNED, ROOM_DELETED, MEMBER_ROLE_CHANGED events; wire ManageRoomModal |

---

### Task 1: Database Migrations

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/015-create-room-bans.sql`
- Create: `backend/src/main/resources/db/changelog/changes/016-add-cascade-deletes.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration 015 — room_bans table**

Create `backend/src/main/resources/db/changelog/changes/015-create-room-bans.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:015-create-room-bans
CREATE TABLE room_bans (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    banned_by_id BIGINT NOT NULL REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (room_id, user_id)
);

CREATE INDEX idx_room_bans_room_id ON room_bans(room_id);
```

- [ ] **Step 2: Create migration 016 — add cascade deletes**

Create `backend/src/main/resources/db/changelog/changes/016-add-cascade-deletes.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:016-add-cascade-deletes

-- chat_room_members.room_id
ALTER TABLE chat_room_members DROP CONSTRAINT IF EXISTS chat_room_members_room_id_fkey;
ALTER TABLE chat_room_members ADD CONSTRAINT chat_room_members_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- messages.room_id
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_room_id_fkey;
ALTER TABLE messages ADD CONSTRAINT messages_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- read_receipts.room_id
ALTER TABLE read_receipts DROP CONSTRAINT IF EXISTS read_receipts_room_id_fkey;
ALTER TABLE read_receipts ADD CONSTRAINT read_receipts_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- room_invitations.room_id
ALTER TABLE room_invitations DROP CONSTRAINT IF EXISTS room_invitations_room_id_fkey;
ALTER TABLE room_invitations ADD CONSTRAINT room_invitations_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- attachments.message_id (cascade from message deletion)
ALTER TABLE attachments DROP CONSTRAINT IF EXISTS attachments_message_id_fkey;
ALTER TABLE attachments ADD CONSTRAINT attachments_message_id_fkey
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE;
```

- [ ] **Step 3: Register migrations in changelog**

Add to the end of `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/changes/015-create-room-bans.sql
  - include:
      file: db/changelog/changes/016-add-cascade-deletes.sql
```

- [ ] **Step 4: Verify migrations compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/015-create-room-bans.sql backend/src/main/resources/db/changelog/changes/016-add-cascade-deletes.sql backend/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add room_bans table and cascade delete migrations"
```

---

### Task 2: MemberRole Enum + RoomBan Entity + Repository

**Files:**
- Modify: `backend/src/main/java/com/chatapp/entity/MemberRole.java`
- Create: `backend/src/main/java/com/chatapp/entity/RoomBan.java`
- Create: `backend/src/main/java/com/chatapp/repository/RoomBanRepository.java`

- [ ] **Step 1: Add ADMIN to MemberRole enum**

Replace contents of `backend/src/main/java/com/chatapp/entity/MemberRole.java`:

```java
package com.chatapp.entity;

public enum MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}
```

- [ ] **Step 2: Create RoomBan entity**

Create `backend/src/main/java/com/chatapp/entity/RoomBan.java`:

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
@Table(name = "room_bans", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "user_id"})
})
public class RoomBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banned_by_id", nullable = false)
    private User bannedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

}
```

- [ ] **Step 3: Create RoomBanRepository**

Create `backend/src/main/java/com/chatapp/repository/RoomBanRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.RoomBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomBanRepository extends JpaRepository<RoomBan, Long> {

    List<RoomBan> findByRoomId(UUID roomId);

    Optional<RoomBan> findByRoomIdAndUserId(UUID roomId, Long userId);

    boolean existsByRoomIdAndUserId(UUID roomId, Long userId);

    void deleteByRoomIdAndUserId(UUID roomId, Long userId);

}
```

- [ ] **Step 4: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/MemberRole.java backend/src/main/java/com/chatapp/entity/RoomBan.java backend/src/main/java/com/chatapp/repository/RoomBanRepository.java
git commit -m "feat: add ADMIN role and RoomBan entity"
```

---

### Task 3: DTOs

**Files:**
- Create: `backend/src/main/java/com/chatapp/dto/BanUserRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/ChangeRoleRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/UpdateRoomRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/RoomBanResponse.java`

- [ ] **Step 1: Create BanUserRequest**

Create `backend/src/main/java/com/chatapp/dto/BanUserRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BanUserRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String reason;

}
```

- [ ] **Step 2: Create ChangeRoleRequest**

Create `backend/src/main/java/com/chatapp/dto/ChangeRoleRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotBlank(message = "Role is required")
    private String role;

}
```

- [ ] **Step 3: Create UpdateRoomRequest**

Create `backend/src/main/java/com/chatapp/dto/UpdateRoomRequest.java`:

```java
package com.chatapp.dto;

import lombok.Data;

@Data
public class UpdateRoomRequest {

    private String name;

    private String description;

}
```

- [ ] **Step 4: Create RoomBanResponse**

Create `backend/src/main/java/com/chatapp/dto/RoomBanResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.RoomBan;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RoomBanResponse {

    private Long id;
    private UUID roomId;
    private Long userId;
    private String username;
    private String displayName;
    private Long bannedById;
    private String bannedByUsername;
    private String reason;
    private Instant createdAt;

    public static RoomBanResponse fromEntity(RoomBan ban) {
        RoomBanResponse response = new RoomBanResponse();
        response.setId(ban.getId());
        response.setRoomId(ban.getRoom().getId());
        response.setUserId(ban.getUser().getId());
        response.setUsername(ban.getUser().getUsername());
        response.setDisplayName(ban.getUser().getDisplayName());
        response.setBannedById(ban.getBannedBy().getId());
        response.setBannedByUsername(ban.getBannedBy().getUsername());
        response.setReason(ban.getReason());
        response.setCreatedAt(ban.getCreatedAt());
        return response;
    }

}
```

- [ ] **Step 5: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/chatapp/dto/BanUserRequest.java backend/src/main/java/com/chatapp/dto/ChangeRoleRequest.java backend/src/main/java/com/chatapp/dto/UpdateRoomRequest.java backend/src/main/java/com/chatapp/dto/RoomBanResponse.java
git commit -m "feat: add moderation DTOs"
```

---

### Task 4: ModerationService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/ModerationService.java`

- [ ] **Step 1: Create ModerationService**

Create `backend/src/main/java/com/chatapp/service/ModerationService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomBan;
import com.chatapp.entity.User;
import com.chatapp.exception.NotRoomMemberException;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.RoomBanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final ChatRoomMemberRepository memberRepository;
    private final RoomBanRepository banRepository;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public RoomBan banUser(UUID roomId, Long targetUserId, Long callerUserId, String reason) {
        ChatRoom room = chatRoomService.findById(roomId);
        ChatRoomMember caller = getMemberOrThrow(roomId, callerUserId);
        validateModeratorRole(caller);

        if (targetUserId.equals(callerUserId)) {
            throw new IllegalArgumentException("Cannot ban yourself");
        }

        // Check if target is owner — nobody can ban the owner
        if (room.getOwner().getId().equals(targetUserId)) {
            throw new IllegalStateException("Cannot ban the room owner");
        }

        // Check caller can act on target's role
        ChatRoomMember target = memberRepository.findByRoomIdAndUserId(roomId, targetUserId).orElse(null);
        if (target != null) {
            validateCallerOutranksTarget(caller, target);
        }

        if (banRepository.existsByRoomIdAndUserId(roomId, targetUserId)) {
            throw new IllegalStateException("User is already banned from this room");
        }

        // Remove from room if currently a member
        if (target != null) {
            memberRepository.delete(target);
        }

        RoomBan ban = new RoomBan();
        ban.setRoom(room);
        ban.setUser(target != null ? target.getUser() : userService.findById(targetUserId));
        ban.setBannedBy(caller.getUser());
        ban.setReason(reason);
        ban = banRepository.save(ban);

        // Notify banned user
        messagingTemplate.convertAndSendToUser(
                String.valueOf(targetUserId),
                "/queue/notifications",
                Map.of("type", "ROOM_BANNED", "data", Map.of("roomId", roomId, "roomName", room.getName()))
        );

        // Broadcast member role change event to room
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/events",
                Map.of("type", "MEMBER_REMOVED", "data", Map.of("userId", targetUserId))
        );

        return ban;
    }

    @Transactional
    public void unbanUser(UUID roomId, Long targetUserId, Long callerUserId) {
        ChatRoomMember caller = getMemberOrThrow(roomId, callerUserId);
        validateModeratorRole(caller);

        if (!banRepository.existsByRoomIdAndUserId(roomId, targetUserId)) {
            throw new IllegalArgumentException("User is not banned from this room");
        }

        banRepository.deleteByRoomIdAndUserId(roomId, targetUserId);
    }

    public List<RoomBan> getBannedUsers(UUID roomId, Long callerUserId) {
        ChatRoomMember caller = getMemberOrThrow(roomId, callerUserId);
        validateModeratorRole(caller);
        return banRepository.findByRoomId(roomId);
    }

    @Transactional
    public ChatRoomMember changeRole(UUID roomId, Long targetUserId, MemberRole newRole, Long callerUserId) {
        ChatRoom room = chatRoomService.findById(roomId);

        // Only OWNER can change roles
        if (!room.getOwner().getId().equals(callerUserId)) {
            throw new IllegalStateException("Only the room owner can change member roles");
        }

        if (targetUserId.equals(callerUserId)) {
            throw new IllegalArgumentException("Cannot change your own role");
        }

        if (newRole == MemberRole.OWNER) {
            throw new IllegalArgumentException("Cannot assign OWNER role");
        }

        ChatRoomMember target = getMemberOrThrow(roomId, targetUserId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new IllegalStateException("Cannot change the owner's role");
        }

        target.setRole(newRole);
        target = memberRepository.save(target);

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/events",
                Map.of("type", "MEMBER_ROLE_CHANGED", "data", Map.of(
                        "userId", targetUserId,
                        "username", target.getUser().getUsername(),
                        "newRole", newRole.name()
                ))
        );

        return target;
    }

    @Transactional
    public RoomBan kickMember(UUID roomId, Long targetUserId, Long callerUserId) {
        return banUser(roomId, targetUserId, callerUserId, null);
    }

    private ChatRoomMember getMemberOrThrow(UUID roomId, Long userId) {
        return memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotRoomMemberException("Not a member of this room"));
    }

    private void validateModeratorRole(ChatRoomMember member) {
        if (member.getRole() == MemberRole.MEMBER) {
            throw new IllegalStateException("Insufficient permissions");
        }
    }

    private void validateCallerOutranksTarget(ChatRoomMember caller, ChatRoomMember target) {
        // OWNER can act on anyone. ADMIN can act on ADMIN and MEMBER but not OWNER.
        if (caller.getRole() == MemberRole.OWNER) return;
        if (target.getRole() == MemberRole.OWNER) {
            throw new IllegalStateException("Cannot perform this action on the room owner");
        }
    }

}
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/ModerationService.java
git commit -m "feat: add ModerationService with permission hierarchy"
```

---

### Task 5: Extend ChatRoomService — Update Room, Delete Room, Ban Check on Join

**Files:**
- Modify: `backend/src/main/java/com/chatapp/service/ChatRoomService.java`
- Modify: `backend/src/main/java/com/chatapp/repository/AttachmentRepository.java`

- [ ] **Step 1: Add RoomBanRepository and AttachmentRepository imports to ChatRoomService**

Add new dependencies and methods to `backend/src/main/java/com/chatapp/service/ChatRoomService.java`.

Add imports and fields — replace the class declaration and field section:

```java
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final RoomBanRepository banRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
```

Add the required imports at the top of the file:

```java
import com.chatapp.repository.RoomBanRepository;
import com.chatapp.repository.AttachmentRepository;
```

- [ ] **Step 2: Add ban check to joinRoom method**

In `ChatRoomService.joinRoom`, add a ban check after the `findById` call:

```java
    @Transactional
    public ChatRoomMember joinRoom(UUID roomId, User user) {
        ChatRoom room = findById(roomId);

        if (banRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            throw new IllegalStateException("You are banned from this room");
        }

        if (memberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            return memberRepository.findByRoomIdAndUserId(roomId, user.getId()).get();
        }

        ChatRoomMember membership = new ChatRoomMember();
        membership.setRoom(room);
        membership.setUser(user);
        membership.setRole(MemberRole.MEMBER);
        return memberRepository.save(membership);
    }
```

- [ ] **Step 3: Add updateRoom method**

Add to `ChatRoomService`:

```java
    @Transactional
    public ChatRoom updateRoom(UUID roomId, String name, String description, Long callerUserId) {
        ChatRoom room = findById(roomId);

        ChatRoomMember caller = memberRepository.findByRoomIdAndUserId(roomId, callerUserId)
                .orElseThrow(() -> new NotRoomMemberException("Not a member of this room"));

        if (caller.getRole() == MemberRole.MEMBER) {
            throw new IllegalStateException("Insufficient permissions");
        }

        if (name != null && !name.isBlank()) {
            if (room.getType() == RoomType.PUBLIC && !name.equals(room.getName())
                    && roomRepository.existsByNameAndType(name, RoomType.PUBLIC)) {
                throw new RoomNameAlreadyExistsException("A public room with this name already exists");
            }
            room.setName(name);
        }
        if (description != null) {
            room.setDescription(description);
        }

        return roomRepository.save(room);
    }
```

- [ ] **Step 4: Add deleteRoom method**

Add to `ChatRoomService`:

```java
    @Transactional
    public void deleteRoom(UUID roomId, Long callerUserId) {
        ChatRoom room = findById(roomId);

        if (!room.getOwner().getId().equals(callerUserId)) {
            throw new IllegalStateException("Only the room owner can delete the room");
        }

        // Clean up attachment files from disk before cascade delete removes DB records
        List<com.chatapp.entity.Attachment> attachments = attachmentRepository.findByMessageRoomId(roomId);
        for (com.chatapp.entity.Attachment attachment : attachments) {
            fileStorageService.delete(attachment.getStoragePath());
        }

        roomRepository.delete(room);
    }
```

- [ ] **Step 5: Add findByMessageRoomId to AttachmentRepository**

Add to `backend/src/main/java/com/chatapp/repository/AttachmentRepository.java`:

```java
    List<Attachment> findByMessageRoomId(UUID roomId);
```

- [ ] **Step 6: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/ChatRoomService.java backend/src/main/java/com/chatapp/repository/AttachmentRepository.java
git commit -m "feat: add room update, delete, and ban check on join"
```

---

### Task 6: Extend MessageService — Admin Deletion

**Files:**
- Modify: `backend/src/main/java/com/chatapp/service/MessageService.java`

- [ ] **Step 1: Update deleteMessage to check for ADMIN role**

Replace the `deleteMessage` method in `backend/src/main/java/com/chatapp/service/MessageService.java`:

```java
    @Transactional
    public Message deleteMessage(UUID messageId, Long userId, UUID roomId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("Message does not belong to this room");
        }

        boolean isSender = message.getSender().getId().equals(userId);
        boolean isOwner = message.getRoom().getOwner().getId().equals(userId);
        boolean isAdmin = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .map(member -> member.getRole() == MemberRole.ADMIN)
                .orElse(false);

        if (!isSender && !isOwner && !isAdmin) {
            throw new IllegalStateException("Only the sender, room owner, or admin can delete a message");
        }

        message.setDeletedAt(Instant.now());
        return messageRepository.save(message);
    }
```

Add the import at the top:

```java
import com.chatapp.entity.MemberRole;
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/MessageService.java
git commit -m "feat: allow admin message deletion"
```

---

### Task 7: ModerationController

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/ModerationController.java`

- [ ] **Step 1: Create ModerationController**

Create `backend/src/main/java/com/chatapp/controller/ModerationController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.BanUserRequest;
import com.chatapp.dto.ChangeRoleRequest;
import com.chatapp.dto.ChatRoomMemberResponse;
import com.chatapp.dto.RoomBanResponse;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomBan;
import com.chatapp.service.ModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PutMapping("/members/{userId}/role")
    public ResponseEntity<ChatRoomMemberResponse> changeRole(
            @PathVariable UUID roomId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        MemberRole newRole = MemberRole.valueOf(request.getRole().toUpperCase());
        ChatRoomMember updated = moderationService.changeRole(roomId, userId, newRole, callerId);
        return ResponseEntity.ok(ChatRoomMemberResponse.fromEntity(updated));
    }

    @PostMapping("/bans")
    public ResponseEntity<RoomBanResponse> banUser(
            @PathVariable UUID roomId,
            @Valid @RequestBody BanUserRequest request,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        RoomBan ban = moderationService.banUser(roomId, request.getUserId(), callerId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomBanResponse.fromEntity(ban));
    }

    @DeleteMapping("/bans/{userId}")
    public ResponseEntity<Void> unbanUser(
            @PathVariable UUID roomId,
            @PathVariable Long userId,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        moderationService.unbanUser(roomId, userId, callerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/bans")
    public ResponseEntity<List<RoomBanResponse>> getBannedUsers(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        List<RoomBanResponse> bans = moderationService.getBannedUsers(roomId, callerId).stream()
                .map(RoomBanResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(bans);
    }

}
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/ModerationController.java
git commit -m "feat: add ModerationController REST endpoints"
```

---

### Task 8: Extend ChatRoomController — Update Room, Delete Room, Kick Member

**Files:**
- Modify: `backend/src/main/java/com/chatapp/controller/ChatRoomController.java`

- [ ] **Step 1: Add new endpoints to ChatRoomController**

Add imports to the top of `ChatRoomController.java`:

```java
import com.chatapp.dto.UpdateRoomRequest;
import com.chatapp.service.ModerationService;
```

Add `ModerationService` field:

```java
    private final ModerationService moderationService;
```

Add the three new endpoints after the existing `getMembers` method:

```java
    @PutMapping("/{roomId}")
    public ResponseEntity<ChatRoomResponse> updateRoom(
            @PathVariable UUID roomId,
            @RequestBody UpdateRoomRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ChatRoom room = chatRoomService.updateRoom(roomId, request.getName(), request.getDescription(), userId);
        long memberCount = chatRoomService.getMemberCount(roomId);
        return ResponseEntity.ok(ChatRoomResponse.fromEntity(room, memberCount));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.deleteRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roomId}/members/{memberId}")
    public ResponseEntity<Void> kickMember(
            @PathVariable UUID roomId,
            @PathVariable Long memberId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        moderationService.kickMember(roomId, memberId, userId);
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing tests**

Run: `cd backend && ./gradlew test`
Expected: All existing tests PASS (no regressions from ADMIN role addition)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/ChatRoomController.java
git commit -m "feat: add room update, delete, and kick endpoints"
```

---

### Task 9: Frontend Types and API Layer

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/moderation.ts`
- Modify: `frontend/src/api/rooms.ts`

- [ ] **Step 1: Add new types to types.ts**

Add to the end of `frontend/src/api/types.ts` (before the closing of the file):

```typescript
export interface RoomBan {
  id: number;
  roomId: string;
  userId: number;
  username: string;
  displayName: string | null;
  bannedById: number;
  bannedByUsername: string;
  reason: string | null;
  createdAt: string;
}

export interface BanUserRequest {
  userId: number;
  reason?: string;
}

export interface UpdateRoomRequest {
  name?: string;
  description?: string;
}

export interface ChangeRoleRequest {
  role: string;
}

export interface RoomBannedNotification {
  type: 'ROOM_BANNED';
  data: { roomId: string; roomName: string };
}

export interface RoomDeletedEvent {
  type: 'ROOM_DELETED';
  data: { roomId: string };
}

export interface MemberRoleChangedEvent {
  type: 'MEMBER_ROLE_CHANGED';
  data: { userId: number; username: string; newRole: string };
}

export interface MemberRemovedEvent {
  type: 'MEMBER_REMOVED';
  data: { userId: number };
}
```

Update the `MessageEvent` type to include new event types:

Replace:
```typescript
export interface MessageEvent {
  type: 'MESSAGE_EDITED' | 'MESSAGE_DELETED';
  data: ChatMessage | { messageId: string };
}
```

With:
```typescript
export interface MessageEvent {
  type: 'MESSAGE_EDITED' | 'MESSAGE_DELETED' | 'ROOM_DELETED' | 'MEMBER_ROLE_CHANGED' | 'MEMBER_REMOVED';
  data: ChatMessage | { messageId: string } | { roomId: string } | { userId: number; username: string; newRole: string } | { userId: number };
}
```

- [ ] **Step 2: Create moderation API**

Create `frontend/src/api/moderation.ts`:

```typescript
import client from './client.ts';
import type { RoomBan, ChatRoomMember } from './types.ts';

export const moderationApi = {
  banUser: async (roomId: string, userId: number, reason?: string): Promise<RoomBan> => {
    const { data } = await client.post<RoomBan>(`/rooms/${roomId}/bans`, { userId, reason });
    return data;
  },

  unbanUser: async (roomId: string, userId: number): Promise<void> => {
    await client.delete(`/rooms/${roomId}/bans/${userId}`);
  },

  getBannedUsers: async (roomId: string): Promise<RoomBan[]> => {
    const { data } = await client.get<RoomBan[]>(`/rooms/${roomId}/bans`);
    return data;
  },

  changeRole: async (roomId: string, userId: number, role: string): Promise<ChatRoomMember> => {
    const { data } = await client.put<ChatRoomMember>(`/rooms/${roomId}/members/${userId}/role`, { role });
    return data;
  },

  kickMember: async (roomId: string, userId: number): Promise<void> => {
    await client.delete(`/rooms/${roomId}/members/${userId}`);
  },
};
```

- [ ] **Step 3: Add room update and delete to rooms API**

Add to `frontend/src/api/rooms.ts` inside the `roomsApi` object:

```typescript
  updateRoom: async (roomId: string, name?: string, description?: string): Promise<ChatRoom> => {
    const { data } = await client.put<ChatRoom>(`/rooms/${roomId}`, { name, description });
    return data;
  },

  deleteRoom: async (roomId: string): Promise<void> => {
    await client.delete(`/rooms/${roomId}`);
  },
```

- [ ] **Step 4: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/moderation.ts frontend/src/api/rooms.ts
git commit -m "feat: add moderation API layer and types"
```

---

### Task 10: ManageRoomModal Component

**Files:**
- Create: `frontend/src/components/ManageRoomModal.tsx`

- [ ] **Step 1: Create ManageRoomModal**

Create `frontend/src/components/ManageRoomModal.tsx`:

```tsx
import { useState, useEffect, useCallback } from 'react';
import { Modal, Tabs, List, Button, Input, Tag, Popconfirm, message, Space, Typography } from 'antd';
import { DeleteOutlined, StopOutlined, CrownOutlined, UserDeleteOutlined } from '@ant-design/icons';
import type { ChatRoom, ChatRoomMember, RoomBan } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { moderationApi } from '../api/moderation.ts';

const { Text, Title } = Typography;
const { TextArea } = Input;

interface Props {
  open: boolean;
  room: ChatRoom;
  currentUserId: number;
  onClose: () => void;
  onRoomUpdated: (room: ChatRoom) => void;
  onRoomDeleted: () => void;
}

export const ManageRoomModal = ({ open, room, currentUserId, onClose, onRoomUpdated, onRoomDeleted }: Props) => {
  const [members, setMembers] = useState<ChatRoomMember[]>([]);
  const [bans, setBans] = useState<RoomBan[]>([]);
  const [editName, setEditName] = useState(room.name);
  const [editDescription, setEditDescription] = useState(room.description || '');
  const [loading, setLoading] = useState(false);

  const isOwner = room.ownerId === currentUserId;
  const currentMember = members.find(m => m.userId === currentUserId);
  const isAdmin = currentMember?.role === 'ADMIN' || isOwner;

  const loadMembers = useCallback(async () => {
    try {
      const data = await roomsApi.getMembers(room.id);
      setMembers(data);
    } catch (err) {
      console.error('Failed to load members', err);
    }
  }, [room.id]);

  const loadBans = useCallback(async () => {
    if (!isAdmin) return;
    try {
      const data = await moderationApi.getBannedUsers(room.id);
      setBans(data);
    } catch (err) {
      console.error('Failed to load bans', err);
    }
  }, [room.id, isAdmin]);

  useEffect(() => {
    if (open) {
      loadMembers();
      loadBans();
      setEditName(room.name);
      setEditDescription(room.description || '');
    }
  }, [open, loadMembers, loadBans, room.name, room.description]);

  const handleChangeRole = async (userId: number, newRole: string) => {
    try {
      await moderationApi.changeRole(room.id, userId, newRole);
      message.success(`Role updated to ${newRole}`);
      loadMembers();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to change role');
    }
  };

  const handleBan = async (userId: number) => {
    try {
      await moderationApi.banUser(room.id, userId);
      message.success('User banned');
      loadMembers();
      loadBans();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to ban user');
    }
  };

  const handleKick = async (userId: number) => {
    try {
      await moderationApi.kickMember(room.id, userId);
      message.success('User removed');
      loadMembers();
      loadBans();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to remove user');
    }
  };

  const handleUnban = async (userId: number) => {
    try {
      await moderationApi.unbanUser(room.id, userId);
      message.success('User unbanned');
      loadBans();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to unban user');
    }
  };

  const handleSaveSettings = async () => {
    setLoading(true);
    try {
      const updated = await roomsApi.updateRoom(room.id, editName, editDescription);
      message.success('Room settings updated');
      onRoomUpdated(updated);
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to update room');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteRoom = async () => {
    setLoading(true);
    try {
      await roomsApi.deleteRoom(room.id);
      message.success('Room deleted');
      onRoomDeleted();
      onClose();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to delete room');
    } finally {
      setLoading(false);
    }
  };

  const canActOnMember = (member: ChatRoomMember): boolean => {
    if (member.userId === currentUserId) return false;
    if (member.role === 'OWNER') return false;
    if (isOwner) return true;
    if (currentMember?.role === 'ADMIN') return true;
    return false;
  };

  const getRoleTag = (role: string) => {
    if (role === 'OWNER') return <Tag color="gold">Owner</Tag>;
    if (role === 'ADMIN') return <Tag color="blue">Admin</Tag>;
    return <Tag>Member</Tag>;
  };

  const membersTab = (
    <List
      dataSource={members}
      renderItem={(member) => (
        <List.Item
          actions={canActOnMember(member) ? [
            isOwner && member.role === 'MEMBER' && (
              <Button size="small" icon={<CrownOutlined />} onClick={() => handleChangeRole(member.userId, 'ADMIN')}>
                Make Admin
              </Button>
            ),
            isOwner && member.role === 'ADMIN' && (
              <Button size="small" onClick={() => handleChangeRole(member.userId, 'MEMBER')}>
                Remove Admin
              </Button>
            ),
            <Popconfirm title="Ban this user from the room?" onConfirm={() => handleBan(member.userId)} okText="Ban" okType="danger">
              <Button size="small" danger icon={<StopOutlined />}>Ban</Button>
            </Popconfirm>,
          ].filter(Boolean) : []}
        >
          <List.Item.Meta
            title={<Space>{member.displayName || member.username} {getRoleTag(member.role)}</Space>}
            description={`@${member.username}`}
          />
        </List.Item>
      )}
    />
  );

  const bannedTab = (
    <List
      dataSource={bans}
      locale={{ emptyText: 'No banned users' }}
      renderItem={(ban) => (
        <List.Item
          actions={[
            <Button size="small" onClick={() => handleUnban(ban.userId)}>Unban</Button>
          ]}
        >
          <List.Item.Meta
            title={ban.displayName || ban.username}
            description={
              <Space direction="vertical" size={0}>
                <Text type="secondary">@{ban.username}</Text>
                <Text type="secondary">Banned by @{ban.bannedByUsername}</Text>
                {ban.reason && <Text type="secondary">Reason: {ban.reason}</Text>}
              </Space>
            }
          />
        </List.Item>
      )}
    />
  );

  const settingsTab = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <Text strong>Room Name</Text>
        <Input value={editName} onChange={(e) => setEditName(e.target.value)} />
      </div>
      <div>
        <Text strong>Description</Text>
        <TextArea rows={3} value={editDescription} onChange={(e) => setEditDescription(e.target.value)} />
      </div>
      <Button type="primary" onClick={handleSaveSettings} loading={loading}>
        Save Changes
      </Button>
      {isOwner && (
        <div style={{ marginTop: 24, borderTop: '1px solid #f0f0f0', paddingTop: 16 }}>
          <Title level={5} type="danger">Danger Zone</Title>
          <Popconfirm
            title="Delete this room?"
            description="This will permanently delete the room and all its messages. This cannot be undone."
            onConfirm={handleDeleteRoom}
            okText="Delete"
            okType="danger"
          >
            <Button danger icon={<DeleteOutlined />} loading={loading}>
              Delete Room
            </Button>
          </Popconfirm>
        </div>
      )}
    </div>
  );

  const tabItems = [
    { key: 'members', label: `Members (${members.length})`, children: membersTab },
    ...(isAdmin ? [{ key: 'banned', label: `Banned (${bans.length})`, children: bannedTab }] : []),
    ...(isAdmin ? [{ key: 'settings', label: 'Settings', children: settingsTab }] : []),
  ];

  return (
    <Modal
      title={`Manage: ${room.name}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={600}
    >
      <Tabs items={tabItems} />
    </Modal>
  );
};
```

- [ ] **Step 2: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ManageRoomModal.tsx
git commit -m "feat: add ManageRoomModal component"
```

---

### Task 11: Extend RoomHeader — Add Manage Button

**Files:**
- Modify: `frontend/src/components/RoomHeader.tsx`

- [ ] **Step 1: Add manage button to RoomHeader**

Replace contents of `frontend/src/components/RoomHeader.tsx`:

```tsx
import { Button, Typography } from 'antd';
import { LogoutOutlined, UserAddOutlined, SettingOutlined } from '@ant-design/icons';
import type { ChatRoom, ChatRoomMember, PresenceStatus } from '../api/types.ts';
import { PresenceIndicator } from './PresenceIndicator.tsx';

const { Text, Title } = Typography;

interface Props {
  room: ChatRoom;
  currentUserId: number;
  currentUserRole?: string;
  onLeave: () => void;
  onInvite?: () => void;
  onManage?: () => void;
  dmPresence?: PresenceStatus;
}

export const RoomHeader = ({ room, currentUserId, currentUserRole, onLeave, onInvite, onManage, dmPresence }: Props) => {
  const isOwner = room.ownerId === currentUserId;
  const isDirect = room.type === 'DIRECT';
  const isPrivate = room.type === 'PRIVATE';
  const canManage = isOwner || currentUserRole === 'ADMIN';

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
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Title level={5} style={{ margin: 0 }}>{displayName}</Title>
          {dmPresence && <PresenceIndicator status={dmPresence} size={10} />}
        </div>
        {room.description && <Text type="secondary">{room.description}</Text>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Text type="secondary">{room.memberCount} members</Text>
        {canManage && !isDirect && onManage && (
          <Button size="small" icon={<SettingOutlined />} onClick={onManage}>Manage</Button>
        )}
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

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/RoomHeader.tsx
git commit -m "feat: add manage button to RoomHeader for admins and owners"
```

---

### Task 12: Extend MessageBubble — Admin Delete

**Files:**
- Modify: `frontend/src/components/MessageBubble.tsx`

- [ ] **Step 1: Add isRoomAdmin prop to MessageBubble**

In `frontend/src/components/MessageBubble.tsx`, update the interface and the `canDelete` logic.

Replace the interface:

```typescript
interface MessageBubbleProps {
  message: ChatMessage;
  isOwn: boolean;
  isRoomOwner: boolean;
  isRoomAdmin?: boolean;
  onReply?: (message: ChatMessage) => void;
  onEdit?: (message: ChatMessage) => void;
  onDelete?: (messageId: string) => void;
  onReplyClick?: (messageId: string) => void;
}
```

Update the component signature:

```typescript
export const MessageBubble = ({ message, isOwn, isRoomOwner, isRoomAdmin, onReply, onEdit, onDelete, onReplyClick }: MessageBubbleProps) => {
```

Replace the `canDelete` line:

```typescript
  const canDelete = (isOwn || isRoomOwner || isRoomAdmin) && onDelete;
```

- [ ] **Step 2: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/MessageBubble.tsx
git commit -m "feat: allow admin message deletion in MessageBubble"
```

---

### Task 13: Extend useWebSocket — Notification Queue Subscription

**Files:**
- Modify: `frontend/src/hooks/useWebSocket.ts`

- [ ] **Step 1: Add onNotification callback and /queue/notifications subscription**

In `frontend/src/hooks/useWebSocket.ts`, add a new callback option for notifications.

Update the `UseWebSocketOptions` interface:

```typescript
interface UseWebSocketOptions {
  onMessage: (roomId: string, message: ChatMessage) => void;
  onPresence?: (event: PresenceEvent) => void;
  onEvent?: (roomId: string, event: MessageEvent) => void;
  onNotification?: (notification: any) => void;
}
```

Update the function signature to destructure `onNotification`:

```typescript
export function useWebSocket({ onMessage, onPresence, onEvent, onNotification }: UseWebSocketOptions) {
```

Add a ref for `onNotification` alongside the existing refs:

```typescript
  const onNotificationRef = useRef(onNotification);
  onNotificationRef.current = onNotification;
```

Inside the `onConnect` callback, after the `/topic/presence` subscription, add:

```typescript
        stompClient.subscribe('/user/queue/notifications', (msg: IMessage) => {
          const notification = JSON.parse(msg.body);
          onNotificationRef.current?.(notification);
        });
```

- [ ] **Step 2: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useWebSocket.ts
git commit -m "feat: subscribe to user notification queue in useWebSocket"
```

---

### Task 14: Extend ChatLayout — Wire Everything Together

**Files:**
- Modify: `frontend/src/pages/ChatLayout.tsx`

- [ ] **Step 1: Add imports and state for ManageRoomModal and member role tracking**

Add imports to `ChatLayout.tsx`:

```typescript
import { ManageRoomModal } from '../components/ManageRoomModal.tsx';
import type { ChatRoomMember } from '../api/types.ts';
```

Add state variables after the existing state declarations:

```typescript
  const [manageOpen, setManageOpen] = useState(false);
  const [currentUserRole, setCurrentUserRole] = useState<string | undefined>(undefined);
```

- [ ] **Step 2: Add notification handler**

Add a `handleNotification` callback before the `useWebSocket` call:

```typescript
  const handleNotification = useCallback((notification: any) => {
    if (notification.type === 'ROOM_BANNED') {
      const { roomId, roomName } = notification.data;
      setRooms(prev => prev.filter(r => r.id !== roomId));
      if (selectedRoomRef.current?.id === roomId) {
        setSelectedRoom(null);
      }
    }
  }, []);
```

- [ ] **Step 3: Extend the handleEvent callback**

Add handling for new event types inside the `handleEvent` callback, after the `MESSAGE_DELETED` block:

```typescript
    } else if (event.type === 'ROOM_DELETED') {
      setRooms(prev => prev.filter(r => r.id !== roomId));
      if (selectedRoomRef.current?.id === roomId) {
        setSelectedRoom(null);
      }
    } else if (event.type === 'MEMBER_ROLE_CHANGED') {
      const { userId, newRole } = event.data as { userId: number; username: string; newRole: string };
      if (userId === user!.id && selectedRoomRef.current?.id === roomId) {
        setCurrentUserRole(newRole);
      }
    } else if (event.type === 'MEMBER_REMOVED') {
      const { userId } = event.data as { userId: number };
      if (userId === user!.id) {
        setRooms(prev => prev.filter(r => r.id !== roomId));
        if (selectedRoomRef.current?.id === roomId) {
          setSelectedRoom(null);
        }
      }
    }
```

- [ ] **Step 4: Pass onNotification to useWebSocket**

Update the useWebSocket call:

```typescript
  const { subscribe, unsubscribe, sendMessage } = useWebSocket({
    onMessage: handleNewMessage,
    onPresence: handlePresenceEvent,
    onEvent: handleEvent,
    onNotification: handleNotification,
  });
```

- [ ] **Step 5: Fetch current user role when selecting a room**

In `handleSelectRoom`, after loading messages, add:

```typescript
    // Fetch current user's role in this room
    try {
      const members = await roomsApi.getMembers(room.id);
      const me = members.find(m => m.userId === user!.id);
      setCurrentUserRole(me?.role);
    } catch (err) {
      console.error('Failed to load member role', err);
    }
```

- [ ] **Step 6: Pass new props to RoomHeader and ChatArea**

Update the `RoomHeader` usage:

```tsx
              <RoomHeader
                room={selectedRoom}
                currentUserId={user!.id}
                currentUserRole={currentUserRole}
                onLeave={handleLeaveRoom}
                onInvite={selectedRoom.type === 'PRIVATE' ? () => setInviteOpen(true) : undefined}
                onManage={() => setManageOpen(true)}
                dmPresence={getDmPresence()}
              />
```

Update `ChatArea` to pass `isRoomAdmin` — in the `ChatArea` component, the `isRoomOwner` prop is passed to `MessageBubble`. We need to also pass `isRoomAdmin`. This requires updating `ChatArea.tsx` props.

In `ChatArea.tsx`, add a new prop `isRoomAdmin`:

Add to the ChatArea props interface:
```typescript
  isRoomAdmin?: boolean;
```

Pass it to each `MessageBubble`:
```typescript
  isRoomAdmin={isRoomAdmin}
```

Then in `ChatLayout.tsx`, pass:
```tsx
              <ChatArea
                messages={currentMessages}
                currentUserId={user!.id}
                roomOwnerId={selectedRoom.ownerId}
                isRoomAdmin={currentUserRole === 'ADMIN' || currentUserRole === 'OWNER'}
                onLoadMore={handleLoadMore}
                loading={loadingMessages}
                onReply={setReplyTo}
                onEdit={setEditingMessage}
                onDelete={handleDeleteMessage}
                onFileDrop={handleFileDrop}
              />
```

- [ ] **Step 7: Add ManageRoomModal and handlers**

Add handlers for room updated/deleted:

```typescript
  const handleRoomUpdated = useCallback((updatedRoom: ChatRoom) => {
    setRooms(prev => prev.map(r => r.id === updatedRoom.id ? updatedRoom : r));
    if (selectedRoom?.id === updatedRoom.id) {
      setSelectedRoom(updatedRoom);
    }
  }, [selectedRoom]);

  const handleRoomDeleted = useCallback(() => {
    if (selectedRoom) {
      unsubscribe(selectedRoom.id);
      setRooms(prev => prev.filter(r => r.id !== selectedRoom.id));
      setSelectedRoom(null);
    }
    setManageOpen(false);
  }, [selectedRoom, unsubscribe]);
```

Add the modal after `InviteToRoomModal`:

```tsx
      {selectedRoom && (
        <ManageRoomModal
          open={manageOpen}
          room={selectedRoom}
          currentUserId={user!.id}
          onClose={() => setManageOpen(false)}
          onRoomUpdated={handleRoomUpdated}
          onRoomDeleted={handleRoomDeleted}
        />
      )}
```

- [ ] **Step 8: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 9: Commit**

```bash
git add frontend/src/pages/ChatLayout.tsx frontend/src/components/ChatArea.tsx
git commit -m "feat: wire moderation UI in ChatLayout with event handling"
```

---

### Task 15: Update ChatArea to Pass isRoomAdmin to MessageBubble

**Files:**
- Modify: `frontend/src/components/ChatArea.tsx`

- [ ] **Step 1: Add isRoomAdmin prop to ChatArea**

In `ChatArea.tsx`, add `isRoomAdmin?: boolean` to the props interface, and pass it through to each `MessageBubble`:

Find the `MessageBubble` render and add the prop:

```tsx
            <MessageBubble
              key={msg.id}
              message={msg}
              isOwn={msg.senderId === currentUserId}
              isRoomOwner={msg.senderId !== currentUserId && currentUserId === roomOwnerId}
              isRoomAdmin={isRoomAdmin}
              onReply={onReply}
              onEdit={msg.senderId === currentUserId ? onEdit : undefined}
              onDelete={onDelete}
              onReplyClick={scrollToMessage}
            />
```

- [ ] **Step 2: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ChatArea.tsx
git commit -m "feat: pass isRoomAdmin prop through ChatArea to MessageBubble"
```

---

### Task 16: Final Integration Verification

- [ ] **Step 1: Build and run full stack**

Run: `docker-compose up --build`
Expected: All services start, migrations run successfully

- [ ] **Step 2: Run backend tests**

Run: `cd backend && ./gradlew test`
Expected: All existing tests pass

- [ ] **Step 3: Manual smoke test**

1. Create a room, promote a member to admin
2. Admin bans a member — member loses access
3. Admin unbans — member can rejoin
4. Owner edits room name/description
5. Admin deletes another user's message
6. Owner deletes room — all data gone

- [ ] **Step 4: Run frontend lint**

Run: `cd frontend && npm run lint`
Expected: No errors
