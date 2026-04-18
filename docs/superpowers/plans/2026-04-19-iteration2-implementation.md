# Iteration 2: Chat Rooms & Real-time Messaging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can create public chat rooms, browse and join them, and send/receive messages in real-time with scrollable history.

**Architecture:** Three new entities (ChatRoom, ChatRoomMember, Message) with REST endpoints for room CRUD/membership and WebSocket (STOMP over native WebSocket) for real-time messaging. Frontend replaces Home.tsx with ChatLayout: right sidebar (room list) + center chat area. Cursor-based pagination for message history.

**Tech Stack:** Spring Boot 3.5.6, spring-boot-starter-websocket, STOMP, PostgreSQL 16, Liquibase, React 19, Ant Design 5, @stomp/stompjs, TypeScript

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|----------------|
| `backend/src/main/resources/db/changelog/changes/005-create-chat-rooms.sql` | chat_rooms table |
| `backend/src/main/resources/db/changelog/changes/006-create-chat-room-members.sql` | chat_room_members table |
| `backend/src/main/resources/db/changelog/changes/007-create-messages.sql` | messages table |
| `backend/src/main/java/com/chatapp/entity/ChatRoom.java` | ChatRoom JPA entity |
| `backend/src/main/java/com/chatapp/entity/ChatRoomMember.java` | ChatRoomMember JPA entity |
| `backend/src/main/java/com/chatapp/entity/Message.java` | Message JPA entity |
| `backend/src/main/java/com/chatapp/entity/RoomType.java` | Enum: PUBLIC |
| `backend/src/main/java/com/chatapp/entity/MemberRole.java` | Enum: OWNER, MEMBER |
| `backend/src/main/java/com/chatapp/repository/ChatRoomRepository.java` | ChatRoom queries |
| `backend/src/main/java/com/chatapp/repository/ChatRoomMemberRepository.java` | Membership queries |
| `backend/src/main/java/com/chatapp/repository/MessageRepository.java` | Message queries |
| `backend/src/main/java/com/chatapp/dto/CreateRoomRequest.java` | Room creation DTO |
| `backend/src/main/java/com/chatapp/dto/ChatRoomResponse.java` | Room response DTO |
| `backend/src/main/java/com/chatapp/dto/ChatRoomMemberResponse.java` | Member response DTO |
| `backend/src/main/java/com/chatapp/dto/MessageResponse.java` | Message response DTO |
| `backend/src/main/java/com/chatapp/dto/SendMessageRequest.java` | WebSocket message DTO |
| `backend/src/main/java/com/chatapp/service/ChatRoomService.java` | Room CRUD + membership |
| `backend/src/main/java/com/chatapp/service/MessageService.java` | Message persistence + pagination |
| `backend/src/main/java/com/chatapp/controller/ChatRoomController.java` | REST endpoints for rooms |
| `backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java` | STOMP message handler |
| `backend/src/main/java/com/chatapp/config/WebSocketConfig.java` | STOMP/WebSocket configuration |
| `backend/src/main/java/com/chatapp/exception/RoomNotFoundException.java` | Room not found exception |
| `backend/src/main/java/com/chatapp/exception/RoomNameAlreadyExistsException.java` | Duplicate room name |
| `backend/src/main/java/com/chatapp/exception/NotRoomMemberException.java` | Not a room member |

### Backend — Modified Files

| File | Changes |
|------|---------|
| `backend/build.gradle` | Add spring-boot-starter-websocket |
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | Include new migrations |
| `backend/src/main/java/com/chatapp/config/SecurityConfig.java` | Permit `/ws` endpoint |
| `backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java` | Handle new exceptions |

### Frontend — New Files

| File | Responsibility |
|------|----------------|
| `frontend/src/api/rooms.ts` | Room REST API calls |
| `frontend/src/hooks/useWebSocket.ts` | STOMP client lifecycle + subscriptions |
| `frontend/src/pages/ChatLayout.tsx` | Main chat page: header + chat area + sidebar |
| `frontend/src/components/RoomList.tsx` | Right sidebar: joined rooms list |
| `frontend/src/components/RoomBrowser.tsx` | Modal: browse/search/join public rooms |
| `frontend/src/components/CreateRoomModal.tsx` | Modal: create new room |
| `frontend/src/components/ChatArea.tsx` | Message list with infinite scroll |
| `frontend/src/components/MessageBubble.tsx` | Single message display |
| `frontend/src/components/MessageInput.tsx` | Text input + send button |
| `frontend/src/components/RoomHeader.tsx` | Room name, member count, leave button |

### Frontend — Modified Files

| File | Changes |
|------|---------|
| `frontend/package.json` | Add @stomp/stompjs |
| `frontend/src/api/types.ts` | Add ChatRoom, ChatRoomMember, Message types |
| `frontend/src/App.tsx` | Replace Home route with ChatLayout |

---

### Task 1: Database Migrations

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/005-create-chat-rooms.sql`
- Create: `backend/src/main/resources/db/changelog/changes/006-create-chat-room-members.sql`
- Create: `backend/src/main/resources/db/changelog/changes/007-create-messages.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration 005 — chat_rooms table**

Create `backend/src/main/resources/db/changelog/changes/005-create-chat-rooms.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:005-create-chat-rooms
CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    owner_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_rooms_name_unique UNIQUE (name)
);
```

- [ ] **Step 2: Create migration 006 — chat_room_members table**

Create `backend/src/main/resources/db/changelog/changes/006-create-chat-room-members.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:006-create-chat-room-members
CREATE TABLE chat_room_members (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_room_members_unique UNIQUE (room_id, user_id)
);
```

- [ ] **Step 3: Create migration 007 — messages table**

Create `backend/src/main/resources/db/changelog/changes/007-create-messages.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:007-create-messages
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_room_created ON messages (room_id, created_at DESC);
```

- [ ] **Step 4: Update changelog master**

Append to `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/changes/005-create-chat-rooms.sql
  - include:
      file: db/changelog/changes/006-create-chat-room-members.sql
  - include:
      file: db/changelog/changes/007-create-messages.sql
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat: add chat rooms, members, and messages database migrations"
```

---

### Task 2: Backend Entities and Enums

**Files:**
- Create: `backend/src/main/java/com/chatapp/entity/RoomType.java`
- Create: `backend/src/main/java/com/chatapp/entity/MemberRole.java`
- Create: `backend/src/main/java/com/chatapp/entity/ChatRoom.java`
- Create: `backend/src/main/java/com/chatapp/entity/ChatRoomMember.java`
- Create: `backend/src/main/java/com/chatapp/entity/Message.java`

- [ ] **Step 1: Create RoomType enum**

Create `backend/src/main/java/com/chatapp/entity/RoomType.java`:

```java
package com.chatapp.entity;

public enum RoomType {
    PUBLIC
}
```

- [ ] **Step 2: Create MemberRole enum**

Create `backend/src/main/java/com/chatapp/entity/MemberRole.java`:

```java
package com.chatapp.entity;

public enum MemberRole {
    OWNER,
    MEMBER
}
```

- [ ] **Step 3: Create ChatRoom entity**

Create `backend/src/main/java/com/chatapp/entity/ChatRoom.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type = RoomType.PUBLIC;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

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

- [ ] **Step 4: Create ChatRoomMember entity**

Create `backend/src/main/java/com/chatapp/entity/ChatRoomMember.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "chat_room_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_id", "user_id"})
})
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }

}
```

- [ ] **Step 5: Create Message entity**

Create `backend/src/main/java/com/chatapp/entity/Message.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

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

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/
git commit -m "feat: add ChatRoom, ChatRoomMember, and Message entities"
```

---

### Task 3: Backend Repositories

**Files:**
- Create: `backend/src/main/java/com/chatapp/repository/ChatRoomRepository.java`
- Create: `backend/src/main/java/com/chatapp/repository/ChatRoomMemberRepository.java`
- Create: `backend/src/main/java/com/chatapp/repository/MessageRepository.java`

- [ ] **Step 1: Create ChatRoomRepository**

Create `backend/src/main/java/com/chatapp/repository/ChatRoomRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    boolean existsByName(String name);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PUBLIC' AND LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<ChatRoom> searchPublicRooms(String search, Pageable pageable);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PUBLIC'")
    Page<ChatRoom> findAllPublicRooms(Pageable pageable);

}
```

- [ ] **Step 2: Create ChatRoomMemberRepository**

Create `backend/src/main/java/com/chatapp/repository/ChatRoomMemberRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByRoomIdAndUserId(UUID roomId, Long userId);

    Optional<ChatRoomMember> findByRoomIdAndUserId(UUID roomId, Long userId);

    List<ChatRoomMember> findByRoomId(UUID roomId);

    @Query("SELECT m.room FROM ChatRoomMember m WHERE m.user.id = :userId")
    List<com.chatapp.entity.ChatRoom> findRoomsByUserId(Long userId);

    long countByRoomId(UUID roomId);

}
```

- [ ] **Step 3: Create MessageRepository**

Create `backend/src/main/java/com/chatapp/repository/MessageRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID roomId, Instant before, Pageable pageable);

    List<Message> findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/repository/
git commit -m "feat: add ChatRoom, ChatRoomMember, and Message repositories"
```

---

### Task 4: Backend DTOs and Exceptions

**Files:**
- Create: `backend/src/main/java/com/chatapp/dto/CreateRoomRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/SendMessageRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/ChatRoomResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/ChatRoomMemberResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/MessageResponse.java`
- Create: `backend/src/main/java/com/chatapp/exception/RoomNotFoundException.java`
- Create: `backend/src/main/java/com/chatapp/exception/RoomNameAlreadyExistsException.java`
- Create: `backend/src/main/java/com/chatapp/exception/NotRoomMemberException.java`
- Modify: `backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create request DTOs**

Create `backend/src/main/java/com/chatapp/dto/CreateRoomRequest.java`:

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

}
```

Create `backend/src/main/java/com/chatapp/dto/SendMessageRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 3000, message = "Message must not exceed 3000 characters")
    private String content;

}
```

- [ ] **Step 2: Create response DTOs**

Create `backend/src/main/java/com/chatapp/dto/ChatRoomResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.ChatRoom;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ChatRoomResponse {

    private UUID id;
    private String name;
    private String description;
    private String type;
    private Long ownerId;
    private String ownerUsername;
    private long memberCount;
    private Instant createdAt;

    public static ChatRoomResponse fromEntity(ChatRoom room, long memberCount) {
        ChatRoomResponse response = new ChatRoomResponse();
        response.setId(room.getId());
        response.setName(room.getName());
        response.setDescription(room.getDescription());
        response.setType(room.getType().name());
        response.setOwnerId(room.getOwner().getId());
        response.setOwnerUsername(room.getOwner().getUsername());
        response.setMemberCount(memberCount);
        response.setCreatedAt(room.getCreatedAt());
        return response;
    }

}
```

Create `backend/src/main/java/com/chatapp/dto/ChatRoomMemberResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.ChatRoomMember;
import lombok.Data;

import java.time.Instant;

@Data
public class ChatRoomMemberResponse {

    private Long userId;
    private String username;
    private String displayName;
    private String role;
    private Instant joinedAt;

    public static ChatRoomMemberResponse fromEntity(ChatRoomMember member) {
        ChatRoomMemberResponse response = new ChatRoomMemberResponse();
        response.setUserId(member.getUser().getId());
        response.setUsername(member.getUser().getUsername());
        response.setDisplayName(member.getUser().getDisplayName());
        response.setRole(member.getRole().name());
        response.setJoinedAt(member.getJoinedAt());
        return response;
    }

}
```

Create `backend/src/main/java/com/chatapp/dto/MessageResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.Message;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class MessageResponse {

    private UUID id;
    private UUID roomId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public static MessageResponse fromEntity(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRoomId(message.getRoom().getId());
        response.setSenderId(message.getSender().getId());
        response.setSenderUsername(message.getSender().getUsername());
        response.setSenderDisplayName(message.getSender().getDisplayName());
        response.setContent(message.getContent());
        response.setCreatedAt(message.getCreatedAt());
        response.setUpdatedAt(message.getUpdatedAt());
        return response;
    }

}
```

- [ ] **Step 3: Create exception classes**

Create `backend/src/main/java/com/chatapp/exception/RoomNotFoundException.java`:

```java
package com.chatapp.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/com/chatapp/exception/RoomNameAlreadyExistsException.java`:

```java
package com.chatapp.exception;

public class RoomNameAlreadyExistsException extends RuntimeException {
    public RoomNameAlreadyExistsException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/com/chatapp/exception/NotRoomMemberException.java`:

```java
package com.chatapp.exception;

public class NotRoomMemberException extends RuntimeException {
    public NotRoomMemberException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Add exception handlers to GlobalExceptionHandler**

Add these methods to `backend/src/main/java/com/chatapp/exception/GlobalExceptionHandler.java`, after the existing `handleUserNotFound` method:

```java
    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(
            RoomNotFoundException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(RoomNameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleRoomNameAlreadyExists(
            RoomNameAlreadyExistsException ex,
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

    @ExceptionHandler(NotRoomMemberException.class)
    public ResponseEntity<ErrorResponse> handleNotRoomMember(
            NotRoomMemberException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/chatapp/dto/ backend/src/main/java/com/chatapp/exception/
git commit -m "feat: add chat room DTOs and exception handlers"
```

---

### Task 5: Backend Services

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/ChatRoomService.java`
- Create: `backend/src/main/java/com/chatapp/service/MessageService.java`

- [ ] **Step 1: Create ChatRoomService**

Create `backend/src/main/java/com/chatapp/service/ChatRoomService.java`:

```java
package com.chatapp.service;

import com.chatapp.dto.CreateRoomRequest;
import com.chatapp.entity.*;
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

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;

    @Transactional
    public ChatRoom createRoom(CreateRoomRequest request, User owner) {
        if (chatRoomRepository.existsByName(request.getName())) {
            throw new RoomNameAlreadyExistsException("A room with this name already exists");
        }

        ChatRoom room = new ChatRoom();
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setType(RoomType.PUBLIC);
        room.setOwner(owner);
        room = chatRoomRepository.save(room);

        ChatRoomMember ownerMember = new ChatRoomMember();
        ownerMember.setRoom(room);
        ownerMember.setUser(owner);
        ownerMember.setRole(MemberRole.OWNER);
        memberRepository.save(ownerMember);

        return room;
    }

    public ChatRoom findById(UUID roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    public List<ChatRoom> getUserRooms(Long userId) {
        return memberRepository.findRoomsByUserId(userId);
    }

    public Page<ChatRoom> getPublicRooms(String search, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (search != null && !search.isBlank()) {
            return chatRoomRepository.searchPublicRooms(search.trim(), pageRequest);
        }
        return chatRoomRepository.findAllPublicRooms(pageRequest);
    }

    @Transactional
    public void joinRoom(UUID roomId, User user) {
        ChatRoom room = findById(roomId);

        if (memberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            return;
        }

        ChatRoomMember member = new ChatRoomMember();
        member.setRoom(room);
        member.setUser(user);
        member.setRole(MemberRole.MEMBER);
        memberRepository.save(member);
    }

    @Transactional
    public void leaveRoom(UUID roomId, Long userId) {
        ChatRoom room = findById(roomId);

        if (room.getOwner().getId().equals(userId)) {
            throw new IllegalStateException("Owner cannot leave the room");
        }

        ChatRoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotRoomMemberException("You are not a member of this room"));

        memberRepository.delete(member);
    }

    public List<ChatRoomMember> getMembers(UUID roomId) {
        findById(roomId);
        return memberRepository.findByRoomId(roomId);
    }

    public void validateMembership(UUID roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new NotRoomMemberException("You are not a member of this room");
        }
    }

    public long getMemberCount(UUID roomId) {
        return memberRepository.countByRoomId(roomId);
    }

}
```

- [ ] **Step 2: Create MessageService**

Create `backend/src/main/java/com/chatapp/service/MessageService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.MessageRepository;
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

    @Transactional
    public Message sendMessage(ChatRoom room, User sender, String content) {
        Message message = new Message();
        message.setRoom(room);
        message.setSender(sender);
        message.setContent(content);
        return messageRepository.save(message);
    }

    public List<Message> getMessages(UUID roomId, Instant before, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (before != null) {
            return messageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(roomId, before, pageRequest);
        }
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageRequest);
    }

}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/
git commit -m "feat: add ChatRoomService and MessageService"
```

---

### Task 6: Backend REST Controller

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/ChatRoomController.java`
- Modify: `backend/src/main/java/com/chatapp/config/SecurityConfig.java`

- [ ] **Step 1: Create ChatRoomController**

Create `backend/src/main/java/com/chatapp/controller/ChatRoomController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.*;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User owner = userService.findById(userId);
        ChatRoom room = chatRoomService.createRoom(request, owner);
        long memberCount = chatRoomService.getMemberCount(room.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatRoomResponse.fromEntity(room, memberCount));
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ChatRoom> rooms = chatRoomService.getUserRooms(userId);
        List<ChatRoomResponse> responses = rooms.stream()
                .map(r -> ChatRoomResponse.fromEntity(r, chatRoomService.getMemberCount(r.getId())))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/public")
    public ResponseEntity<Page<ChatRoomResponse>> getPublicRooms(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ChatRoom> rooms = chatRoomService.getPublicRooms(search, page, size);
        Page<ChatRoomResponse> responses = rooms.map(r ->
                ChatRoomResponse.fromEntity(r, chatRoomService.getMemberCount(r.getId())));
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ChatRoomResponse> getRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        ChatRoom room = chatRoomService.findById(roomId);
        long memberCount = chatRoomService.getMemberCount(roomId);
        return ResponseEntity.ok(ChatRoomResponse.fromEntity(room, memberCount));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Void> joinRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);
        chatRoomService.joinRoom(roomId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.leaveRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<ChatRoomMemberResponse>> getMembers(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        List<ChatRoomMember> members = chatRoomService.getMembers(roomId);
        List<ChatRoomMemberResponse> responses = members.stream()
                .map(ChatRoomMemberResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Instant before,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        List<Message> messages = messageService.getMessages(roomId, before, Math.min(limit, 100));
        List<MessageResponse> responses = messages.stream()
                .map(MessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

}
```

- [ ] **Step 2: Update SecurityConfig to permit WebSocket endpoint**

In `backend/src/main/java/com/chatapp/config/SecurityConfig.java`, update the `requestMatchers` line to include `/ws`:

```java
                .requestMatchers("/api/users/signup", "/api/auth/signin", "/api/auth/me", "/ws/**").permitAll()
```

- [ ] **Step 3: Verify build**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/ChatRoomController.java backend/src/main/java/com/chatapp/config/SecurityConfig.java
git commit -m "feat: add ChatRoomController with REST endpoints"
```

---

### Task 7: Backend WebSocket Configuration and Controller

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/chatapp/config/WebSocketConfig.java`
- Create: `backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java`

- [ ] **Step 1: Add WebSocket dependency**

Add to `backend/build.gradle` dependencies:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

- [ ] **Step 2: Create WebSocketConfig**

Create `backend/src/main/java/com/chatapp/config/WebSocketConfig.java`:

```java
package com.chatapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

}
```

- [ ] **Step 3: Create WebSocketMessageController**

Create `backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.MessageResponse;
import com.chatapp.dto.SendMessageRequest;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.security.SessionConstants;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable UUID roomId,
            @Payload SendMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        Long userId = (Long) headerAccessor.getSessionAttributes().get(SessionConstants.SESSION_USER_KEY);
        if (userId == null) {
            return;
        }

        chatRoomService.validateMembership(roomId, userId);

        User sender = userService.findById(userId);
        ChatRoom room = chatRoomService.findById(roomId);
        Message message = messageService.sendMessage(room, sender, request.getContent());

        MessageResponse response = MessageResponse.fromEntity(message);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/messages", response);
    }

}
```

- [ ] **Step 4: Verify build**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle backend/src/main/java/com/chatapp/config/WebSocketConfig.java backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java
git commit -m "feat: add WebSocket STOMP configuration and message controller"
```

---

### Task 8: Frontend Types and API Layer

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/rooms.ts`
- Modify: `frontend/package.json`

- [ ] **Step 1: Add @stomp/stompjs dependency**

Run: `cd frontend && npm install @stomp/stompjs`

- [ ] **Step 2: Add types to `frontend/src/api/types.ts`**

Append to `frontend/src/api/types.ts`:

```typescript
export interface ChatRoom {
  id: string;
  name: string;
  description: string | null;
  type: string;
  ownerId: number;
  ownerUsername: string;
  memberCount: number;
  createdAt: string;
}

export interface ChatRoomMember {
  userId: number;
  username: string;
  displayName: string | null;
  role: string;
  joinedAt: string;
}

export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: number;
  senderUsername: string;
  senderDisplayName: string | null;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoomRequest {
  name: string;
  description?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
```

- [ ] **Step 3: Create rooms API module**

Create `frontend/src/api/rooms.ts`:

```typescript
import client from './client.ts';
import type { ChatRoom, ChatRoomMember, ChatMessage, CreateRoomRequest, PageResponse } from './types.ts';

export const roomsApi = {
  createRoom: async (request: CreateRoomRequest): Promise<ChatRoom> => {
    const { data } = await client.post<ChatRoom>('/rooms', request);
    return data;
  },

  getMyRooms: async (): Promise<ChatRoom[]> => {
    const { data } = await client.get<ChatRoom[]>('/rooms');
    return data;
  },

  getPublicRooms: async (search?: string, page = 0, size = 20): Promise<PageResponse<ChatRoom>> => {
    const params = new URLSearchParams();
    if (search) params.set('search', search);
    params.set('page', String(page));
    params.set('size', String(size));
    const { data } = await client.get<PageResponse<ChatRoom>>(`/rooms/public?${params}`);
    return data;
  },

  getRoom: async (roomId: string): Promise<ChatRoom> => {
    const { data } = await client.get<ChatRoom>(`/rooms/${roomId}`);
    return data;
  },

  joinRoom: async (roomId: string): Promise<void> => {
    await client.post(`/rooms/${roomId}/join`);
  },

  leaveRoom: async (roomId: string): Promise<void> => {
    await client.post(`/rooms/${roomId}/leave`);
  },

  getMembers: async (roomId: string): Promise<ChatRoomMember[]> => {
    const { data } = await client.get<ChatRoomMember[]>(`/rooms/${roomId}/members`);
    return data;
  },

  getMessages: async (roomId: string, before?: string, limit = 50): Promise<ChatMessage[]> => {
    const params = new URLSearchParams();
    if (before) params.set('before', before);
    params.set('limit', String(limit));
    const { data } = await client.get<ChatMessage[]>(`/rooms/${roomId}/messages?${params}`);
    return data;
  },
};
```

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/api/types.ts frontend/src/api/rooms.ts
git commit -m "feat: add chat room types and API module"
```

---

### Task 9: Frontend WebSocket Hook

**Files:**
- Create: `frontend/src/hooks/useWebSocket.ts`

- [ ] **Step 1: Create useWebSocket hook**

Create `frontend/src/hooks/useWebSocket.ts`:

```typescript
import { useRef, useCallback, useEffect } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import type { ChatMessage } from '../api/types.ts';

const WS_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/api$/, '').replace(/^http/, 'ws') + '/ws';

export function useWebSocket(onMessage: (roomId: string, message: ChatMessage) => void) {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }>>(new Map());

  useEffect(() => {
    const stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: {},
    });

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      subscriptionsRef.current.forEach(sub => sub.unsubscribe());
      subscriptionsRef.current.clear();
      stompClient.deactivate();
    };
  }, []);

  const subscribe = useCallback((roomId: string) => {
    const client = clientRef.current;
    if (!client?.connected) return;

    if (subscriptionsRef.current.has(roomId)) return;

    const subscription = client.subscribe(`/topic/rooms/${roomId}/messages`, (msg: IMessage) => {
      const message: ChatMessage = JSON.parse(msg.body);
      onMessage(roomId, message);
    });

    subscriptionsRef.current.set(roomId, subscription);
  }, [onMessage]);

  const unsubscribe = useCallback((roomId: string) => {
    const sub = subscriptionsRef.current.get(roomId);
    if (sub) {
      sub.unsubscribe();
      subscriptionsRef.current.delete(roomId);
    }
  }, []);

  const sendMessage = useCallback((roomId: string, content: string) => {
    const client = clientRef.current;
    if (!client?.connected) return;

    client.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify({ content }),
    });
  }, []);

  return { subscribe, unsubscribe, sendMessage };
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/hooks/useWebSocket.ts
git commit -m "feat: add useWebSocket hook for STOMP messaging"
```

---

### Task 10: Frontend Chat Components

**Files:**
- Create: `frontend/src/components/MessageBubble.tsx`
- Create: `frontend/src/components/MessageInput.tsx`
- Create: `frontend/src/components/RoomHeader.tsx`
- Create: `frontend/src/components/ChatArea.tsx`
- Create: `frontend/src/components/RoomList.tsx`
- Create: `frontend/src/components/CreateRoomModal.tsx`
- Create: `frontend/src/components/RoomBrowser.tsx`

- [ ] **Step 1: Create MessageBubble**

Create `frontend/src/components/MessageBubble.tsx`:

```tsx
import { Typography } from 'antd';
import type { ChatMessage } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  message: ChatMessage;
  isOwn: boolean;
}

export const MessageBubble = ({ message, isOwn }: Props) => {
  return (
    <div style={{
      display: 'flex',
      justifyContent: isOwn ? 'flex-end' : 'flex-start',
      marginBottom: 8,
    }}>
      <div style={{
        maxWidth: '70%',
        padding: '8px 12px',
        borderRadius: 8,
        backgroundColor: isOwn ? '#1890ff' : '#f0f0f0',
        color: isOwn ? '#fff' : '#000',
      }}>
        {!isOwn && (
          <Text strong style={{ fontSize: 12, display: 'block', color: '#1890ff' }}>
            {message.senderDisplayName || message.senderUsername}
          </Text>
        )}
        <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{message.content}</div>
        <Text style={{ fontSize: 11, opacity: 0.7, color: isOwn ? '#e0e0e0' : '#999' }}>
          {new Date(message.createdAt).toLocaleTimeString()}
        </Text>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Create MessageInput**

Create `frontend/src/components/MessageInput.tsx`:

```tsx
import { useState } from 'react';
import { Input, Button } from 'antd';
import { SendOutlined } from '@ant-design/icons';

interface Props {
  onSend: (content: string) => void;
  disabled?: boolean;
}

export const MessageInput = ({ onSend, disabled }: Props) => {
  const [value, setValue] = useState('');

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setValue('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div style={{ display: 'flex', gap: 8, padding: '12px 16px', borderTop: '1px solid #f0f0f0' }}>
      <Input.TextArea
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Type a message..."
        autoSize={{ minRows: 1, maxRows: 4 }}
        disabled={disabled}
      />
      <Button
        type="primary"
        icon={<SendOutlined />}
        onClick={handleSend}
        disabled={disabled || !value.trim()}
      />
    </div>
  );
};
```

- [ ] **Step 3: Create RoomHeader**

Create `frontend/src/components/RoomHeader.tsx`:

```tsx
import { Button, Typography } from 'antd';
import { LogoutOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';

const { Text, Title } = Typography;

interface Props {
  room: ChatRoom;
  currentUserId: number;
  onLeave: () => void;
}

export const RoomHeader = ({ room, currentUserId, onLeave }: Props) => {
  const isOwner = room.ownerId === currentUserId;

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '12px 16px',
      borderBottom: '1px solid #f0f0f0',
    }}>
      <div>
        <Title level={5} style={{ margin: 0 }}>#{room.name}</Title>
        {room.description && <Text type="secondary">{room.description}</Text>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Text type="secondary">{room.memberCount} members</Text>
        {!isOwner && (
          <Button size="small" icon={<LogoutOutlined />} onClick={onLeave}>Leave</Button>
        )}
      </div>
    </div>
  );
};
```

- [ ] **Step 4: Create ChatArea**

Create `frontend/src/components/ChatArea.tsx`:

```tsx
import { useEffect, useRef, useCallback, useState } from 'react';
import { Spin, Typography } from 'antd';
import { MessageBubble } from './MessageBubble.tsx';
import type { ChatMessage } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  messages: ChatMessage[];
  currentUserId: number;
  onLoadMore: () => Promise<boolean>;
  loading?: boolean;
}

export const ChatArea = ({ messages, currentUserId, onLoadMore, loading }: Props) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [hasMore, setHasMore] = useState(true);
  const isAtBottomRef = useRef(true);

  useEffect(() => {
    if (isAtBottomRef.current && containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [messages]);

  const handleScroll = useCallback(async () => {
    const container = containerRef.current;
    if (!container || loading || !hasMore) return;

    isAtBottomRef.current = container.scrollHeight - container.scrollTop - container.clientHeight < 50;

    if (container.scrollTop < 100) {
      const prevHeight = container.scrollHeight;
      const more = await onLoadMore();
      setHasMore(more);
      requestAnimationFrame(() => {
        container.scrollTop = container.scrollHeight - prevHeight;
      });
    }
  }, [loading, hasMore, onLoadMore]);

  if (messages.length === 0 && !loading) {
    return (
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <Text type="secondary">No messages yet. Start the conversation!</Text>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      style={{ flex: 1, overflowY: 'auto', padding: '16px' }}
    >
      {loading && <div style={{ textAlign: 'center', padding: 8 }}><Spin size="small" /></div>}
      {messages.map((msg) => (
        <MessageBubble key={msg.id} message={msg} isOwn={msg.senderId === currentUserId} />
      ))}
    </div>
  );
};
```

- [ ] **Step 5: Create RoomList**

Create `frontend/src/components/RoomList.tsx`:

```tsx
import { Button, List, Typography } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  rooms: ChatRoom[];
  selectedRoomId: string | null;
  onSelectRoom: (room: ChatRoom) => void;
  onBrowse: () => void;
  onCreate: () => void;
}

export const RoomList = ({ rooms, selectedRoomId, onSelectRoom, onBrowse, onCreate }: Props) => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0' }}>
        <Text strong>Rooms</Text>
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <List
          dataSource={rooms}
          renderItem={(room) => (
            <List.Item
              onClick={() => onSelectRoom(room)}
              style={{
                cursor: 'pointer',
                padding: '8px 16px',
                backgroundColor: room.id === selectedRoomId ? '#e6f7ff' : 'transparent',
              }}
            >
              <Text>#{room.name}</Text>
            </List.Item>
          )}
        />
      </div>
      <div style={{ padding: 12, borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8 }}>
        <Button icon={<SearchOutlined />} onClick={onBrowse} block>Browse</Button>
        <Button icon={<PlusOutlined />} onClick={onCreate} type="primary" block>Create</Button>
      </div>
    </div>
  );
};
```

- [ ] **Step 6: Create CreateRoomModal**

Create `frontend/src/components/CreateRoomModal.tsx`:

```tsx
import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
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
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="Room Name" rules={[{ required: true, message: 'Room name is required' }]}>
          <Input placeholder="e.g. general" />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea placeholder="What is this room about?" rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
};
```

- [ ] **Step 7: Create RoomBrowser**

Create `frontend/src/components/RoomBrowser.tsx`:

```tsx
import { useState, useEffect } from 'react';
import { Modal, Input, List, Button, Typography, message } from 'antd';
import { roomsApi } from '../api/rooms.ts';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onJoined: (room: ChatRoom) => void;
  joinedRoomIds: Set<string>;
}

export const RoomBrowser = ({ open, onClose, onJoined, joinedRoomIds }: Props) => {
  const [search, setSearch] = useState('');
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) fetchRooms();
  }, [open, search]);

  const fetchRooms = async () => {
    setLoading(true);
    try {
      const result = await roomsApi.getPublicRooms(search || undefined);
      setRooms(result.content);
    } catch {
      message.error('Failed to load rooms');
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async (room: ChatRoom) => {
    try {
      await roomsApi.joinRoom(room.id);
      message.success(`Joined #${room.name}`);
      onJoined(room);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to join room');
    }
  };

  return (
    <Modal title="Browse Public Rooms" open={open} onCancel={onClose} footer={null} width={600}>
      <Input.Search
        placeholder="Search rooms..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        style={{ marginBottom: 16 }}
        allowClear
      />
      <List
        loading={loading}
        dataSource={rooms}
        renderItem={(room) => (
          <List.Item
            actions={[
              joinedRoomIds.has(room.id)
                ? <Text type="secondary">Joined</Text>
                : <Button size="small" type="primary" onClick={() => handleJoin(room)}>Join</Button>
            ]}
          >
            <List.Item.Meta
              title={`#${room.name}`}
              description={room.description || 'No description'}
            />
            <Text type="secondary">{room.memberCount} members</Text>
          </List.Item>
        )}
      />
    </Modal>
  );
};
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/
git commit -m "feat: add chat UI components (messages, rooms, modals)"
```

---

### Task 11: Frontend ChatLayout and Routing

**Files:**
- Create: `frontend/src/pages/ChatLayout.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Create ChatLayout**

Create `frontend/src/pages/ChatLayout.tsx`:

```tsx
import { useState, useEffect, useCallback } from 'react';
import { message } from 'antd';
import { AppHeader } from '../components/AppHeader.tsx';
import { RoomList } from '../components/RoomList.tsx';
import { RoomBrowser } from '../components/RoomBrowser.tsx';
import { CreateRoomModal } from '../components/CreateRoomModal.tsx';
import { ChatArea } from '../components/ChatArea.tsx';
import { MessageInput } from '../components/MessageInput.tsx';
import { RoomHeader } from '../components/RoomHeader.tsx';
import { useWebSocket } from '../hooks/useWebSocket.ts';
import { useAuth } from '../contexts/AuthContext.tsx';
import { roomsApi } from '../api/rooms.ts';
import type { ChatRoom, ChatMessage } from '../api/types.ts';

export const ChatLayout = () => {
  const { user } = useAuth();
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [selectedRoom, setSelectedRoom] = useState<ChatRoom | null>(null);
  const [messages, setMessages] = useState<Map<string, ChatMessage[]>>(new Map());
  const [browserOpen, setBrowserOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);

  const handleNewMessage = useCallback((roomId: string, msg: ChatMessage) => {
    setMessages(prev => {
      const updated = new Map(prev);
      const existing = updated.get(roomId) || [];
      updated.set(roomId, [...existing, msg]);
      return updated;
    });
  }, []);

  const { subscribe, unsubscribe, sendMessage } = useWebSocket(handleNewMessage);

  useEffect(() => {
    roomsApi.getMyRooms().then(setRooms).catch(() => message.error('Failed to load rooms'));
  }, []);

  const handleSelectRoom = useCallback(async (room: ChatRoom) => {
    if (selectedRoom?.id) unsubscribe(selectedRoom.id);
    setSelectedRoom(room);

    if (!messages.has(room.id)) {
      setLoadingMessages(true);
      try {
        const msgs = await roomsApi.getMessages(room.id);
        setMessages(prev => {
          const updated = new Map(prev);
          updated.set(room.id, msgs.reverse());
          return updated;
        });
      } catch {
        message.error('Failed to load messages');
      } finally {
        setLoadingMessages(false);
      }
    }

    subscribe(room.id);
  }, [selectedRoom, messages, subscribe, unsubscribe]);

  const handleLoadMore = useCallback(async (): Promise<boolean> => {
    if (!selectedRoom) return false;
    const currentMessages = messages.get(selectedRoom.id) || [];
    if (currentMessages.length === 0) return false;

    const oldest = currentMessages[0];
    const older = await roomsApi.getMessages(selectedRoom.id, oldest.createdAt);
    if (older.length === 0) return false;

    setMessages(prev => {
      const updated = new Map(prev);
      const existing = updated.get(selectedRoom.id) || [];
      updated.set(selectedRoom.id, [...older.reverse(), ...existing]);
      return updated;
    });
    return older.length >= 50;
  }, [selectedRoom, messages]);

  const handleSendMessage = useCallback((content: string) => {
    if (selectedRoom) sendMessage(selectedRoom.id, content);
  }, [selectedRoom, sendMessage]);

  const handleLeaveRoom = async () => {
    if (!selectedRoom) return;
    try {
      await roomsApi.leaveRoom(selectedRoom.id);
      setRooms(prev => prev.filter(r => r.id !== selectedRoom.id));
      unsubscribe(selectedRoom.id);
      setSelectedRoom(null);
      message.success(`Left #${selectedRoom.name}`);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to leave room');
    }
  };

  const handleRoomCreated = (room: ChatRoom) => {
    setRooms(prev => [...prev, room]);
    handleSelectRoom(room);
  };

  const handleRoomJoined = (room: ChatRoom) => {
    if (!rooms.find(r => r.id === room.id)) {
      setRooms(prev => [...prev, room]);
    }
    handleSelectRoom(room);
    setBrowserOpen(false);
  };

  const currentMessages = selectedRoom ? (messages.get(selectedRoom.id) || []) : [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Chat Area */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          {selectedRoom ? (
            <>
              <RoomHeader room={selectedRoom} currentUserId={user!.id} onLeave={handleLeaveRoom} />
              <ChatArea
                messages={currentMessages}
                currentUserId={user!.id}
                onLoadMore={handleLoadMore}
                loading={loadingMessages}
              />
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
          />
        </div>
      </div>

      <RoomBrowser
        open={browserOpen}
        onClose={() => setBrowserOpen(false)}
        onJoined={handleRoomJoined}
        joinedRoomIds={new Set(rooms.map(r => r.id))}
      />
      <CreateRoomModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleRoomCreated}
      />
    </div>
  );
};
```

- [ ] **Step 2: Update App.tsx routing**

Replace the `Home` route in `frontend/src/App.tsx`. Change the import and route:

Replace:
```tsx
import { Home } from './pages/Home.tsx';
```
With:
```tsx
import { ChatLayout } from './pages/ChatLayout.tsx';
```

Replace the `/` route:
```tsx
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Home />
              </ProtectedRoute>
            }
          />
```
With:
```tsx
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <ChatLayout />
              </ProtectedRoute>
            }
          />
```

- [ ] **Step 3: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/ChatLayout.tsx frontend/src/App.tsx
git commit -m "feat: add ChatLayout page and replace Home route"
```

---

## Running the App

```bash
docker-compose up --build
```

Then open `http://localhost:5173` — sign in, create a room, browse/join rooms, send messages.
