# Iteration 4: Presence, Status & Notifications — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users see online/AFK/offline status for other users, and unread message counts per room with mark-as-read behavior.

**Architecture:** In-memory presence tracking via `ConcurrentHashMap` (not persisted). New `ReadReceipt` entity for unread counts. Presence changes broadcast via `/topic/presence`. Client sends periodic heartbeats and `visibilitychange` signals. Frontend extends `useWebSocket` with heartbeat/presence logic and adds `PresenceIndicator` + `UnreadBadge` components.

**Tech Stack:** Spring Boot 3.5.6, spring-boot-starter-websocket, STOMP, PostgreSQL 16, Liquibase, React 19, Ant Design 5, @stomp/stompjs, TypeScript

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|----------------|
| `backend/src/main/resources/db/changelog/changes/012-create-read-receipts.sql` | read_receipts table |
| `backend/src/main/java/com/chatapp/entity/ReadReceipt.java` | ReadReceipt JPA entity |
| `backend/src/main/java/com/chatapp/entity/PresenceStatus.java` | Enum: ONLINE, AFK, OFFLINE |
| `backend/src/main/java/com/chatapp/repository/ReadReceiptRepository.java` | ReadReceipt queries |
| `backend/src/main/java/com/chatapp/dto/MarkAsReadRequest.java` | Mark-as-read request DTO |
| `backend/src/main/java/com/chatapp/dto/PresenceStatusRequest.java` | AFK/ACTIVE status signal DTO |
| `backend/src/main/java/com/chatapp/dto/PresenceResponse.java` | Presence event response DTO |
| `backend/src/main/java/com/chatapp/dto/UnreadCountResponse.java` | Unread count per room DTO |
| `backend/src/main/java/com/chatapp/service/PresenceService.java` | In-memory presence tracking and broadcast |
| `backend/src/main/java/com/chatapp/service/UnreadService.java` | Read receipts and unread count logic |
| `backend/src/main/java/com/chatapp/controller/PresenceController.java` | Presence REST endpoint |
| `backend/src/main/java/com/chatapp/controller/UnreadController.java` | Unread REST endpoints |
| `backend/src/main/java/com/chatapp/controller/WebSocketPresenceController.java` | Heartbeat and status @MessageMapping handlers |
| `backend/src/main/java/com/chatapp/config/WebSocketEventListener.java` | Connect/disconnect event handlers |

### Backend — Modified Files

| File | Changes |
|------|---------|
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | Include migration 012 |
| `backend/src/main/java/com/chatapp/config/WebSocketConfig.java` | Add `@EnableScheduling` for heartbeat timeout checks |

### Frontend — New Files

| File | Responsibility |
|------|----------------|
| `frontend/src/api/presence.ts` | Presence API calls |
| `frontend/src/api/unread.ts` | Unread API calls |
| `frontend/src/hooks/usePresence.ts` | Presence subscription and state |
| `frontend/src/hooks/useUnread.ts` | Unread counts state management |
| `frontend/src/components/PresenceIndicator.tsx` | Green/yellow/gray status dot |
| `frontend/src/components/UnreadBadge.tsx` | Numeric unread badge |

### Frontend — Modified Files

| File | Changes |
|------|---------|
| `frontend/src/api/types.ts` | Add PresenceStatus, PresenceEvent, UnreadCount, MarkAsReadRequest types |
| `frontend/src/hooks/useWebSocket.ts` | Add heartbeat interval, visibilitychange listener, presence subscription |
| `frontend/src/pages/ChatLayout.tsx` | Integrate usePresence, useUnread hooks; mark-as-read on room select |
| `frontend/src/components/RoomList.tsx` | Add UnreadBadge, PresenceIndicator for DMs |
| `frontend/src/components/RoomHeader.tsx` | Add PresenceIndicator for DM rooms |

---

### Task 1: Database Migration

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/012-create-read-receipts.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration 012 — read_receipts table**

Create `backend/src/main/resources/db/changelog/changes/012-create-read-receipts.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:012-create-read-receipts
CREATE TABLE read_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    last_read_message_id UUID NOT NULL REFERENCES messages(id),
    last_read_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT read_receipts_unique UNIQUE (user_id, room_id)
);
```

- [ ] **Step 2: Register migration in changelog**

Add to the end of `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/changes/012-create-read-receipts.sql
```

- [ ] **Step 3: Verify migration applies**

Run: `cd backend && ./gradlew bootRun`
Expected: Application starts without Liquibase errors. Stop the app after confirming.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/012-create-read-receipts.sql backend/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add read_receipts migration for unread tracking"
```

---

### Task 2: ReadReceipt Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/chatapp/entity/ReadReceipt.java`
- Create: `backend/src/main/java/com/chatapp/repository/ReadReceiptRepository.java`

- [ ] **Step 1: Create ReadReceipt entity**

Create `backend/src/main/java/com/chatapp/entity/ReadReceipt.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "read_receipts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "room_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id", nullable = false)
    private Message lastReadMessage;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

}
```

- [ ] **Step 2: Create ReadReceiptRepository**

Create `backend/src/main/java/com/chatapp/repository/ReadReceiptRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.ReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadReceiptRepository extends JpaRepository<ReadReceipt, Long> {

    Optional<ReadReceipt> findByUserIdAndRoomId(Long userId, UUID roomId);

    @Query("""
        SELECT rr FROM ReadReceipt rr
        WHERE rr.user.id = :userId AND rr.room.id IN :roomIds
    """)
    List<ReadReceipt> findByUserIdAndRoomIdIn(Long userId, List<UUID> roomIds);

}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/ReadReceipt.java backend/src/main/java/com/chatapp/repository/ReadReceiptRepository.java
git commit -m "feat: add ReadReceipt entity and repository"
```

---

### Task 3: PresenceStatus Enum and DTOs

**Files:**
- Create: `backend/src/main/java/com/chatapp/entity/PresenceStatus.java`
- Create: `backend/src/main/java/com/chatapp/dto/MarkAsReadRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/PresenceStatusRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/PresenceResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/UnreadCountResponse.java`

- [ ] **Step 1: Create PresenceStatus enum**

Create `backend/src/main/java/com/chatapp/entity/PresenceStatus.java`:

```java
package com.chatapp.entity;

public enum PresenceStatus {
    ONLINE,
    AFK,
    OFFLINE
}
```

- [ ] **Step 2: Create MarkAsReadRequest DTO**

Create `backend/src/main/java/com/chatapp/dto/MarkAsReadRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MarkAsReadRequest {

    @NotNull(message = "lastReadMessageId is required")
    private UUID lastReadMessageId;

}
```

- [ ] **Step 3: Create PresenceStatusRequest DTO**

Create `backend/src/main/java/com/chatapp/dto/PresenceStatusRequest.java`:

```java
package com.chatapp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresenceStatusRequest {

    private String status;

}
```

- [ ] **Step 4: Create PresenceResponse DTO**

Create `backend/src/main/java/com/chatapp/dto/PresenceResponse.java`:

```java
package com.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresenceResponse {

    private Long userId;
    private String username;
    private String status;

}
```

- [ ] **Step 5: Create UnreadCountResponse DTO**

Create `backend/src/main/java/com/chatapp/dto/UnreadCountResponse.java`:

```java
package com.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UnreadCountResponse {

    private UUID roomId;
    private long count;

}
```

- [ ] **Step 6: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/PresenceStatus.java backend/src/main/java/com/chatapp/dto/MarkAsReadRequest.java backend/src/main/java/com/chatapp/dto/PresenceStatusRequest.java backend/src/main/java/com/chatapp/dto/PresenceResponse.java backend/src/main/java/com/chatapp/dto/UnreadCountResponse.java
git commit -m "feat: add iteration 4 DTOs and PresenceStatus enum"
```

---

### Task 4: PresenceService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/PresenceService.java`

- [ ] **Step 1: Create PresenceService**

Create `backend/src/main/java/com/chatapp/service/PresenceService.java`:

```java
package com.chatapp.service;

import com.chatapp.dto.PresenceResponse;
import com.chatapp.entity.PresenceStatus;
import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<Long, PresenceInfo> presenceMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> offlineTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void connect(Long userId) {
        ScheduledFuture<?> timer = offlineTimers.remove(userId);
        if (timer != null) {
            timer.cancel(false);
        }

        presenceMap.compute(userId, (id, info) -> {
            if (info == null) {
                info = new PresenceInfo();
            }
            info.activeConnections++;
            info.lastHeartbeatAt = Instant.now();
            info.status = PresenceStatus.ONLINE;
            return info;
        });

        broadcastPresence(userId);
    }

    public void disconnect(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.activeConnections = Math.max(0, info.activeConnections - 1);
            info.afkConnections = Math.min(info.afkConnections, info.activeConnections);
            if (info.activeConnections == 0) {
                scheduleOffline(userId);
            }
            return info;
        });
    }

    public void heartbeat(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.lastHeartbeatAt = Instant.now();
            if (info.status == PresenceStatus.AFK && info.afkConnections < info.activeConnections) {
                info.status = PresenceStatus.ONLINE;
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public void setConnectionAFK(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.afkConnections = Math.min(info.afkConnections + 1, info.activeConnections);
            if (info.afkConnections >= info.activeConnections && info.status != PresenceStatus.AFK) {
                info.status = PresenceStatus.AFK;
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public void setConnectionActive(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.afkConnections = Math.max(0, info.afkConnections - 1);
            if (info.status == PresenceStatus.AFK) {
                info.status = PresenceStatus.ONLINE;
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public PresenceStatus getPresence(Long userId) {
        PresenceInfo info = presenceMap.get(userId);
        return info != null ? info.status : PresenceStatus.OFFLINE;
    }

    public List<PresenceResponse> getPresenceForUsers(List<Long> userIds) {
        return userIds.stream()
                .map(userId -> {
                    PresenceStatus status = getPresence(userId);
                    User user = userRepository.findById(userId).orElse(null);
                    String username = user != null ? user.getUsername() : "unknown";
                    return new PresenceResponse(userId, username, status.name());
                })
                .toList();
    }

    @Scheduled(fixedRate = 30000)
    public void checkHeartbeatTimeouts() {
        Instant threshold = Instant.now().minusSeconds(60);
        presenceMap.forEach((userId, info) -> {
            if (info.activeConnections > 0
                    && info.status != PresenceStatus.AFK
                    && info.lastHeartbeatAt.isBefore(threshold)) {
                info.status = PresenceStatus.AFK;
                broadcastPresence(userId);
            }
        });
    }

    private void scheduleOffline(Long userId) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            presenceMap.computeIfPresent(userId, (id, info) -> {
                if (info.activeConnections == 0) {
                    info.status = PresenceStatus.OFFLINE;
                    info.afkConnections = 0;
                    broadcastPresence(userId);
                }
                return info;
            });
            offlineTimers.remove(userId);
        }, 30, TimeUnit.SECONDS);
        offlineTimers.put(userId, timer);
    }

    private void broadcastPresence(Long userId) {
        PresenceInfo info = presenceMap.get(userId);
        if (info == null) return;
        User user = userRepository.findById(userId).orElse(null);
        String username = user != null ? user.getUsername() : "unknown";
        PresenceResponse response = new PresenceResponse(userId, username, info.status.name());
        messagingTemplate.convertAndSend("/topic/presence", response);
    }

    private static class PresenceInfo {
        PresenceStatus status = PresenceStatus.OFFLINE;
        int activeConnections = 0;
        int afkConnections = 0;
        Instant lastHeartbeatAt = Instant.now();
    }

}
```

- [ ] **Step 2: Enable scheduling — add `@EnableScheduling` to WebSocketConfig**

Modify `backend/src/main/java/com/chatapp/config/WebSocketConfig.java` — add `@EnableScheduling` import and annotation:

```java
package com.chatapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }

}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/PresenceService.java backend/src/main/java/com/chatapp/config/WebSocketConfig.java
git commit -m "feat: add PresenceService with in-memory tracking and scheduled heartbeat checks"
```

---

### Task 5: WebSocketEventListener and WebSocketPresenceController

**Files:**
- Create: `backend/src/main/java/com/chatapp/config/WebSocketEventListener.java`
- Create: `backend/src/main/java/com/chatapp/controller/WebSocketPresenceController.java`

- [ ] **Step 1: Create WebSocketEventListener**

Create `backend/src/main/java/com/chatapp/config/WebSocketEventListener.java`:

```java
package com.chatapp.config;

import com.chatapp.security.SessionConstants;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.connect(userId);
            log.debug("WebSocket connected: userId={}", userId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.disconnect(userId);
            log.debug("WebSocket disconnected: userId={}", userId);
        }
    }

    private Long getUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) return null;
        return (Long) sessionAttributes.get(SessionConstants.SESSION_USER_KEY);
    }

}
```

Wait — need to add import for Map. The full file:

Create `backend/src/main/java/com/chatapp/config/WebSocketEventListener.java`:

```java
package com.chatapp.config;

import com.chatapp.security.SessionConstants;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.connect(userId);
            log.debug("WebSocket connected: userId={}", userId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.disconnect(userId);
            log.debug("WebSocket disconnected: userId={}", userId);
        }
    }

    private Long getUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) return null;
        return (Long) sessionAttributes.get(SessionConstants.SESSION_USER_KEY);
    }

}
```

- [ ] **Step 2: Create WebSocketPresenceController**

Create `backend/src/main/java/com/chatapp/controller/WebSocketPresenceController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.PresenceStatusRequest;
import com.chatapp.security.SessionConstants;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketPresenceController {

    private final PresenceService presenceService;

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(SessionConstants.SESSION_USER_KEY);
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    @MessageMapping("/presence/status")
    public void updateStatus(@Payload PresenceStatusRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(SessionConstants.SESSION_USER_KEY);
        if (userId == null) return;

        if ("AFK".equals(request.getStatus())) {
            presenceService.setConnectionAFK(userId);
        } else if ("ACTIVE".equals(request.getStatus())) {
            presenceService.setConnectionActive(userId);
        }
    }

}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/config/WebSocketEventListener.java backend/src/main/java/com/chatapp/controller/WebSocketPresenceController.java
git commit -m "feat: add WebSocket event listener and presence controller for heartbeat/status"
```

---

### Task 6: UnreadService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/UnreadService.java`

- [ ] **Step 1: Create UnreadService**

Create `backend/src/main/java/com/chatapp/service/UnreadService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.ReadReceipt;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.ReadReceiptRepository;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnreadService {

    private final ReadReceiptRepository readReceiptRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional
    public void markAsRead(Long userId, UUID roomId, UUID lastReadMessageId) {
        Message message = messageRepository.findById(lastReadMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        ReadReceipt receipt = readReceiptRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseGet(() -> {
                    ReadReceipt newReceipt = new ReadReceipt();
                    newReceipt.setUser(userRepository.getReferenceById(userId));
                    newReceipt.setRoom(message.getRoom());
                    return newReceipt;
                });

        receipt.setLastReadMessage(message);
        receipt.setLastReadAt(message.getCreatedAt());
        readReceiptRepository.save(receipt);
    }

    public Map<UUID, Long> getUnreadCounts(Long userId) {
        List<ChatRoom> rooms = memberRepository.findRoomsByUserId(userId);
        if (rooms.isEmpty()) return Map.of();

        List<UUID> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        List<ReadReceipt> receipts = readReceiptRepository.findByUserIdAndRoomIdIn(userId, roomIds);

        Map<UUID, Instant> lastReadMap = new HashMap<>();
        for (ReadReceipt rr : receipts) {
            lastReadMap.put(rr.getRoom().getId(), rr.getLastReadAt());
        }

        Map<UUID, Long> counts = new HashMap<>();
        for (UUID roomId : roomIds) {
            Instant lastReadAt = lastReadMap.get(roomId);
            long count;
            if (lastReadAt != null) {
                count = messageRepository.countByRoomIdAndCreatedAtAfter(roomId, lastReadAt);
            } else {
                count = messageRepository.countByRoomId(roomId);
            }
            if (count > 0) {
                counts.put(roomId, count);
            }
        }

        return counts;
    }

}
```

- [ ] **Step 2: Add count queries to MessageRepository**

Add to `backend/src/main/java/com/chatapp/repository/MessageRepository.java`:

```java
    long countByRoomId(UUID roomId);

    long countByRoomIdAndCreatedAtAfter(UUID roomId, Instant after);
```

The full file should be:

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

    long countByRoomId(UUID roomId);

    long countByRoomIdAndCreatedAtAfter(UUID roomId, Instant after);

}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/UnreadService.java backend/src/main/java/com/chatapp/repository/MessageRepository.java
git commit -m "feat: add UnreadService with read receipts and unread count logic"
```

---

### Task 7: PresenceController and UnreadController

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/PresenceController.java`
- Create: `backend/src/main/java/com/chatapp/controller/UnreadController.java`

- [ ] **Step 1: Create PresenceController**

Create `backend/src/main/java/com/chatapp/controller/PresenceController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.PresenceResponse;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class PresenceController {

    private final ChatRoomService chatRoomService;
    private final PresenceService presenceService;

    @GetMapping("/{roomId}/members/presence")
    public ResponseEntity<List<PresenceResponse>> getRoomMemberPresence(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);

        List<Long> memberIds = chatRoomService.getMembers(roomId).stream()
                .map(member -> member.getUser().getId())
                .toList();

        List<PresenceResponse> presences = presenceService.getPresenceForUsers(memberIds);
        return ResponseEntity.ok(presences);
    }

}
```

- [ ] **Step 2: Create UnreadController**

Create `backend/src/main/java/com/chatapp/controller/UnreadController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.MarkAsReadRequest;
import com.chatapp.dto.UnreadCountResponse;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.UnreadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class UnreadController {

    private final UnreadService unreadService;
    private final ChatRoomService chatRoomService;

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID roomId,
            @Valid @RequestBody MarkAsReadRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        unreadService.markAsRead(userId, roomId, request.getLastReadMessageId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread")
    public ResponseEntity<List<UnreadCountResponse>> getUnreadCounts(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UnreadCountResponse> counts = unreadService.getUnreadCounts(userId).entrySet().stream()
                .map(e -> new UnreadCountResponse(e.getKey(), e.getValue()))
                .toList();
        return ResponseEntity.ok(counts);
    }

}
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run existing tests to confirm no regressions**

Run: `cd backend && ./gradlew test`
Expected: All existing tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/PresenceController.java backend/src/main/java/com/chatapp/controller/UnreadController.java
git commit -m "feat: add PresenceController and UnreadController REST endpoints"
```

---

### Task 8: Frontend Types and API Modules

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/presence.ts`
- Create: `frontend/src/api/unread.ts`

- [ ] **Step 1: Add new types to types.ts**

Add at the end of `frontend/src/api/types.ts` (before the closing of the file):

```typescript
export type PresenceStatus = 'ONLINE' | 'AFK' | 'OFFLINE';

export interface PresenceEvent {
  userId: number;
  username: string;
  status: PresenceStatus;
}

export interface UnreadCount {
  roomId: string;
  count: number;
}

export interface MarkAsReadRequest {
  lastReadMessageId: string;
}
```

- [ ] **Step 2: Create presence API module**

Create `frontend/src/api/presence.ts`:

```typescript
import client from './client.ts';
import type { PresenceEvent } from './types.ts';

export const presenceApi = {
  getRoomMemberPresence: async (roomId: string): Promise<PresenceEvent[]> => {
    const { data } = await client.get<PresenceEvent[]>(`/rooms/${roomId}/members/presence`);
    return data;
  },
};
```

- [ ] **Step 3: Create unread API module**

Create `frontend/src/api/unread.ts`:

```typescript
import client from './client.ts';
import type { UnreadCount } from './types.ts';

export const unreadApi = {
  getUnreadCounts: async (): Promise<UnreadCount[]> => {
    const { data } = await client.get<UnreadCount[]>('/rooms/unread');
    return data;
  },

  markAsRead: async (roomId: string, lastReadMessageId: string): Promise<void> => {
    await client.post(`/rooms/${roomId}/read`, { lastReadMessageId });
  },
};
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/presence.ts frontend/src/api/unread.ts
git commit -m "feat: add presence and unread API modules and types"
```

---

### Task 9: Extend useWebSocket with Heartbeat and Visibility

**Files:**
- Modify: `frontend/src/hooks/useWebSocket.ts`

- [ ] **Step 1: Extend useWebSocket**

Replace `frontend/src/hooks/useWebSocket.ts` with:

```typescript
import { useRef, useCallback, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';
import type { ChatMessage, PresenceEvent } from '../api/types.ts';

const WS_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/api$/, '').replace(/^http/, 'ws') + '/ws';

interface UseWebSocketOptions {
  onMessage: (roomId: string, message: ChatMessage) => void;
  onPresence?: (event: PresenceEvent) => void;
}

export function useWebSocket({ onMessage, onPresence }: UseWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }>>(new Map());
  const pendingSubscriptionsRef = useRef<Set<string>>(new Set());
  const onMessageRef = useRef(onMessage);
  const onPresenceRef = useRef(onPresence);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  onMessageRef.current = onMessage;
  onPresenceRef.current = onPresence;

  const doSubscribe = useCallback((client: Client, roomId: string) => {
    if (subscriptionsRef.current.has(roomId)) return;

    const subscription = client.subscribe(`/topic/rooms/${roomId}/messages`, (msg: IMessage) => {
      const message: ChatMessage = JSON.parse(msg.body);
      onMessageRef.current(roomId, message);
    });

    subscriptionsRef.current.set(roomId, subscription);
  }, []);

  useEffect(() => {
    const stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        // Subscribe to pending rooms
        pendingSubscriptionsRef.current.forEach(roomId => {
          doSubscribe(stompClient, roomId);
        });
        pendingSubscriptionsRef.current.clear();

        // Subscribe to presence updates
        stompClient.subscribe('/topic/presence', (msg: IMessage) => {
          const event: PresenceEvent = JSON.parse(msg.body);
          onPresenceRef.current?.(event);
        });

        // Start heartbeat
        heartbeatRef.current = setInterval(() => {
          if (stompClient.connected) {
            stompClient.publish({ destination: '/app/presence/heartbeat', body: '{}' });
          }
        }, 15000);
      },
      onDisconnect: () => {
        if (heartbeatRef.current) {
          clearInterval(heartbeatRef.current);
          heartbeatRef.current = null;
        }
      },
    });

    // Visibility change handler
    const handleVisibilityChange = () => {
      if (!stompClient.connected) return;
      const status = document.hidden ? 'AFK' : 'ACTIVE';
      stompClient.publish({
        destination: '/app/presence/status',
        body: JSON.stringify({ status }),
      });
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (heartbeatRef.current) {
        clearInterval(heartbeatRef.current);
      }
      subscriptionsRef.current.forEach(sub => sub.unsubscribe());
      subscriptionsRef.current.clear();
      pendingSubscriptionsRef.current.clear();
      stompClient.deactivate();
    };
  }, [doSubscribe]);

  const subscribe = useCallback((roomId: string) => {
    const client = clientRef.current;
    if (!client?.connected) {
      pendingSubscriptionsRef.current.add(roomId);
      return;
    }

    doSubscribe(client, roomId);
  }, [doSubscribe]);

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
git commit -m "feat: extend useWebSocket with heartbeat, visibility change, and presence subscription"
```

---

### Task 10: usePresence and useUnread Hooks

**Files:**
- Create: `frontend/src/hooks/usePresence.ts`
- Create: `frontend/src/hooks/useUnread.ts`

- [ ] **Step 1: Create usePresence hook**

Create `frontend/src/hooks/usePresence.ts`:

```typescript
import { useState, useCallback } from 'react';
import type { PresenceStatus, PresenceEvent } from '../api/types.ts';

export function usePresence() {
  const [presenceMap, setPresenceMap] = useState<Map<number, PresenceStatus>>(new Map());

  const handlePresenceEvent = useCallback((event: PresenceEvent) => {
    setPresenceMap(prev => {
      const next = new Map(prev);
      next.set(event.userId, event.status);
      return next;
    });
  }, []);

  const getPresence = useCallback((userId: number): PresenceStatus => {
    return presenceMap.get(userId) || 'OFFLINE';
  }, [presenceMap]);

  return { presenceMap, getPresence, handlePresenceEvent };
}
```

- [ ] **Step 2: Create useUnread hook**

Create `frontend/src/hooks/useUnread.ts`:

```typescript
import { useState, useEffect, useCallback } from 'react';
import { unreadApi } from '../api/unread.ts';

export function useUnread() {
  const [unreadCounts, setUnreadCounts] = useState<Map<string, number>>(new Map());

  useEffect(() => {
    unreadApi.getUnreadCounts()
      .then(counts => {
        const map = new Map<string, number>();
        counts.forEach(c => map.set(c.roomId, c.count));
        setUnreadCounts(map);
      })
      .catch(console.error);
  }, []);

  const incrementUnread = useCallback((roomId: string) => {
    setUnreadCounts(prev => {
      const next = new Map(prev);
      next.set(roomId, (next.get(roomId) || 0) + 1);
      return next;
    });
  }, []);

  const markAsRead = useCallback(async (roomId: string, lastMessageId: string) => {
    setUnreadCounts(prev => {
      const next = new Map(prev);
      next.delete(roomId);
      return next;
    });
    try {
      await unreadApi.markAsRead(roomId, lastMessageId);
    } catch (err) {
      console.error('Failed to mark as read', err);
    }
  }, []);

  const getUnreadCount = useCallback((roomId: string): number => {
    return unreadCounts.get(roomId) || 0;
  }, [unreadCounts]);

  return { unreadCounts, getUnreadCount, incrementUnread, markAsRead };
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/usePresence.ts frontend/src/hooks/useUnread.ts
git commit -m "feat: add usePresence and useUnread hooks"
```

---

### Task 11: PresenceIndicator and UnreadBadge Components

**Files:**
- Create: `frontend/src/components/PresenceIndicator.tsx`
- Create: `frontend/src/components/UnreadBadge.tsx`

- [ ] **Step 1: Create PresenceIndicator component**

Create `frontend/src/components/PresenceIndicator.tsx`:

```typescript
import type { PresenceStatus } from '../api/types.ts';

interface Props {
  status: PresenceStatus;
  size?: number;
}

const statusColors: Record<PresenceStatus, string> = {
  ONLINE: '#52c41a',
  AFK: '#faad14',
  OFFLINE: '#d9d9d9',
};

export const PresenceIndicator = ({ status, size = 8 }: Props) => (
  <span
    title={status.toLowerCase()}
    style={{
      display: 'inline-block',
      width: size,
      height: size,
      borderRadius: '50%',
      backgroundColor: statusColors[status],
      flexShrink: 0,
    }}
  />
);
```

- [ ] **Step 2: Create UnreadBadge component**

Create `frontend/src/components/UnreadBadge.tsx`:

```typescript
interface Props {
  count: number;
}

export const UnreadBadge = ({ count }: Props) => {
  if (count <= 0) return null;

  const display = count > 99 ? '99+' : String(count);

  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      minWidth: 18,
      height: 18,
      padding: '0 5px',
      borderRadius: 9,
      backgroundColor: '#ff4d4f',
      color: '#fff',
      fontSize: 11,
      fontWeight: 600,
      lineHeight: 1,
    }}>
      {display}
    </span>
  );
};
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/PresenceIndicator.tsx frontend/src/components/UnreadBadge.tsx
git commit -m "feat: add PresenceIndicator and UnreadBadge components"
```

---

### Task 12: Extend RoomList with Unread Badges and Presence

**Files:**
- Modify: `frontend/src/components/RoomList.tsx`

- [ ] **Step 1: Update RoomList to accept and display unread counts and presence**

Replace `frontend/src/components/RoomList.tsx` with:

```typescript
import { Button, Typography } from 'antd';
import { PlusOutlined, SearchOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import type { ChatRoom, PresenceStatus } from '../api/types.ts';
import { UnreadBadge } from './UnreadBadge.tsx';
import { PresenceIndicator } from './PresenceIndicator.tsx';

const { Text } = Typography;

interface RoomListProps {
  rooms: ChatRoom[];
  selectedRoomId: string | null;
  onSelectRoom: (room: ChatRoom) => void;
  onBrowse: () => void;
  onCreate: () => void;
  onContacts: () => void;
  onUserSearch: () => void;
  getUnreadCount: (roomId: string) => number;
  getPresence?: (userId: number) => PresenceStatus;
}

export const RoomList = ({ rooms, selectedRoomId, onSelectRoom, onBrowse, onCreate, onContacts, onUserSearch, getUnreadCount, getPresence }: RoomListProps) => {
  const chatRooms = rooms.filter(r => r.type !== 'DIRECT');
  const dmRooms = rooms.filter(r => r.type === 'DIRECT');

  // Sort rooms: unread first, then by name
  const sortByUnread = (a: ChatRoom, b: ChatRoom) => {
    const aUnread = getUnreadCount(a.id);
    const bUnread = getUnreadCount(b.id);
    if (aUnread > 0 && bUnread === 0) return -1;
    if (aUnread === 0 && bUnread > 0) return 1;
    return 0;
  };

  const sortedChatRooms = [...chatRooms].sort(sortByUnread);
  const sortedDmRooms = [...dmRooms].sort(sortByUnread);

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
        {sortedChatRooms.map((room) => {
          const unread = getUnreadCount(room.id);
          return (
            <div
              key={room.id}
              onClick={() => onSelectRoom(room)}
              style={{
                padding: '10px 16px',
                cursor: 'pointer',
                background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
                borderBottom: '1px solid #f5f5f5',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <Text>{room.type === 'PRIVATE' ? '🔒' : '#'}{room.name}</Text>
              <UnreadBadge count={unread} />
            </div>
          );
        })}
        {sortedDmRooms.length > 0 && (
          <>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', borderTop: '1px solid #f0f0f0' }}>
              <Text strong style={{ fontSize: 14 }}>Direct Messages</Text>
            </div>
            {sortedDmRooms.map((room) => {
              const unread = getUnreadCount(room.id);
              // Extract the other user's ID from the DM room name (dm-{minId}-{maxId})
              const dmMatch = room.name.match(/^dm-(\d+)-(\d+)$/);
              const otherUserId = dmMatch ? Number(dmMatch[1]) || Number(dmMatch[2]) : undefined;
              return (
                <div
                  key={room.id}
                  onClick={() => onSelectRoom(room)}
                  style={{
                    padding: '10px 16px',
                    cursor: 'pointer',
                    background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
                    borderBottom: '1px solid #f5f5f5',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <Text>
                    {getPresence && otherUserId && (
                      <PresenceIndicator status={getPresence(otherUserId)} size={8} />
                    )}
                    <UserOutlined style={{ marginRight: 6, marginLeft: getPresence && otherUserId ? 6 : 0 }} />
                    {room.name.replace(/^dm-\d+-\d+$/, 'Direct Message')}
                  </Text>
                  <UnreadBadge count={unread} />
                </div>
              );
            })}
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

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/RoomList.tsx
git commit -m "feat: add unread badges and presence indicators to RoomList"
```

---

### Task 13: Extend RoomHeader with Presence for DMs

**Files:**
- Modify: `frontend/src/components/RoomHeader.tsx`

- [ ] **Step 1: Update RoomHeader to show presence for DM rooms**

Replace `frontend/src/components/RoomHeader.tsx` with:

```typescript
import { Button, Typography } from 'antd';
import { LogoutOutlined, UserAddOutlined } from '@ant-design/icons';
import type { ChatRoom, PresenceStatus } from '../api/types.ts';
import { PresenceIndicator } from './PresenceIndicator.tsx';

const { Text, Title } = Typography;

interface Props {
  room: ChatRoom;
  currentUserId: number;
  onLeave: () => void;
  onInvite?: () => void;
  dmPresence?: PresenceStatus;
}

export const RoomHeader = ({ room, currentUserId, onLeave, onInvite, dmPresence }: Props) => {
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
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Title level={5} style={{ margin: 0 }}>{displayName}</Title>
        {isDirect && dmPresence && (
          <PresenceIndicator status={dmPresence} size={10} />
        )}
        {room.description && <Text type="secondary" style={{ marginLeft: 8 }}>{room.description}</Text>}
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

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/RoomHeader.tsx
git commit -m "feat: add presence indicator to RoomHeader for DM rooms"
```

---

### Task 14: Integrate Hooks into ChatLayout

**Files:**
- Modify: `frontend/src/pages/ChatLayout.tsx`

- [ ] **Step 1: Update ChatLayout to use presence and unread hooks**

Replace `frontend/src/pages/ChatLayout.tsx` with:

```typescript
import { useState, useEffect, useCallback } from 'react';
import type { ChatRoom, ChatMessage } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { useAuth } from '../contexts/AuthContext.tsx';
import { useWebSocket } from '../hooks/useWebSocket.ts';
import { usePresence } from '../hooks/usePresence.ts';
import { useUnread } from '../hooks/useUnread.ts';
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

  const { getPresence, handlePresenceEvent } = usePresence();
  const { getUnreadCount, incrementUnread, markAsRead } = useUnread();

  const selectedRoomRef = useCallback(() => selectedRoom, [selectedRoom]);

  const handleNewMessage = useCallback((roomId: string, msg: ChatMessage) => {
    setMessages(prev => {
      const next = new Map(prev);
      const existing = next.get(roomId) || [];
      if (existing.some(m => m.id === msg.id)) return prev;
      next.set(roomId, [...existing, msg]);
      return next;
    });

    // Increment unread if message is not in the currently selected room
    const currentRoom = selectedRoomRef();
    if (!currentRoom || currentRoom.id !== roomId) {
      incrementUnread(roomId);
    }
  }, [incrementUnread, selectedRoomRef]);

  const { subscribe, unsubscribe, sendMessage } = useWebSocket({
    onMessage: handleNewMessage,
    onPresence: handlePresenceEvent,
  });

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

      // Mark as read
      if (msgs.length > 0) {
        const latestMsg = msgs[0]; // msgs are reversed, so [0] was the last from server (newest)
        markAsRead(room.id, latestMsg.id);
      }
    } catch (err) {
      console.error('Failed to load messages', err);
    } finally {
      setLoadingMessages(false);
    }

    subscribe(room.id);
  }, [selectedRoom, subscribe, unsubscribe, markAsRead]);

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

  // Get DM presence for the other user
  const getDmPresence = () => {
    if (!selectedRoom || selectedRoom.type !== 'DIRECT') return undefined;
    const dmMatch = selectedRoom.name.match(/^dm-(\d+)-(\d+)$/);
    if (!dmMatch) return undefined;
    const id1 = Number(dmMatch[1]);
    const id2 = Number(dmMatch[2]);
    const otherUserId = id1 === user!.id ? id2 : id1;
    return getPresence(otherUserId);
  };

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
                dmPresence={getDmPresence()}
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
            getUnreadCount={getUnreadCount}
            getPresence={getPresence}
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

- [ ] **Step 2: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/ChatLayout.tsx
git commit -m "feat: integrate presence and unread hooks into ChatLayout"
```

---

### Task 15: Smoke Test Full Stack

- [ ] **Step 1: Start the full stack**

Run: `docker-compose up --build`
Expected: All services start. No errors from Liquibase migration 012.

- [ ] **Step 2: Manual smoke test**

Open `http://localhost:5173` in two browser windows:
1. Sign up two users
2. Create a room, have both users join
3. Verify: sending a message from user 1 increments unread badge for user 2 (if user 2 is in a different room)
4. Verify: selecting the room clears the unread badge
5. Verify: presence dots appear next to DM entries (if users have a DM)
6. Close one browser tab — after ~30s, verify the other user shows offline/gray dot

- [ ] **Step 3: Run existing backend tests to confirm no regressions**

Run: `cd backend && ./gradlew test`
Expected: All existing tests PASS

- [ ] **Step 4: Commit (if any fixes were needed)**

Only commit if adjustments were made during smoke testing.

---

## Running the Application

### Full Stack
```bash
docker-compose up --build
```

### Backend Only
```bash
cd backend && ./gradlew bootRun
```

### Frontend Only
```bash
cd frontend && npm run dev
```
