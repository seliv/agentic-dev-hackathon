# Iteration 5: Attachments & Rich Messaging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can upload files/images, edit and delete messages, reply with quoted previews, and use an emoji picker.

**Architecture:** New `Attachment` entity with local filesystem storage and REST upload/download API. Extend `Message` entity with `reply_to_id`, `edited_at`, `deleted_at`. New `/topic/rooms/{roomId}/events` WebSocket channel for edit/delete broadcasts. Message endpoints move from `ChatRoomController` to a new `MessageController` with multipart support. Frontend adds file upload (button/drag-drop/paste), reply bar, edit/delete actions, and lazy-loaded emoji picker.

**Tech Stack:** Spring Boot 3.5.6, spring-boot-starter-websocket, STOMP, PostgreSQL 16, Liquibase, React 19, Ant Design 5, @stomp/stompjs, @emoji-mart/react, TypeScript

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|----------------|
| `backend/src/main/resources/db/changelog/changes/013-create-attachments.sql` | attachments table |
| `backend/src/main/resources/db/changelog/changes/014-add-message-reply-and-edit.sql` | reply_to_id, edited_at, deleted_at columns on messages |
| `backend/src/main/java/com/chatapp/entity/Attachment.java` | Attachment JPA entity |
| `backend/src/main/java/com/chatapp/repository/AttachmentRepository.java` | Attachment queries |
| `backend/src/main/java/com/chatapp/dto/EditMessageRequest.java` | Edit message request DTO |
| `backend/src/main/java/com/chatapp/dto/AttachmentResponse.java` | Attachment response DTO |
| `backend/src/main/java/com/chatapp/dto/ReplyPreview.java` | Reply preview DTO |
| `backend/src/main/java/com/chatapp/dto/MessageEventResponse.java` | WebSocket event wrapper DTO |
| `backend/src/main/java/com/chatapp/service/FileStorageService.java` | File I/O to local filesystem |
| `backend/src/main/java/com/chatapp/service/AttachmentService.java` | Attachment CRUD |
| `backend/src/main/java/com/chatapp/controller/MessageController.java` | Message REST endpoints (moved from ChatRoomController + new edit/delete/multipart) |
| `backend/src/main/java/com/chatapp/controller/AttachmentController.java` | File download endpoints |

### Backend — Modified Files

| File | Changes |
|------|---------|
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | Include migrations 013, 014 |
| `backend/src/main/resources/application.yml` | Multipart config, upload dir |
| `backend/src/main/java/com/chatapp/entity/Message.java` | Add replyTo, editedAt, deletedAt, attachments fields |
| `backend/src/main/java/com/chatapp/dto/SendMessageRequest.java` | Add replyToId field |
| `backend/src/main/java/com/chatapp/dto/MessageResponse.java` | Add replyToId, replyToPreview, editedAt, deleted, attachments fields |
| `backend/src/main/java/com/chatapp/service/MessageService.java` | Add edit, delete, reply support |
| `backend/src/main/java/com/chatapp/controller/ChatRoomController.java` | Remove message endpoints (moved to MessageController) |
| `backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java` | Support replyToId, build full response |
| `docker-compose.yml` | Add uploads volume mount |

### Frontend — New Files

| File | Responsibility |
|------|----------------|
| `frontend/src/api/attachments.ts` | Attachment URL helpers |
| `frontend/src/components/AttachmentPreview.tsx` | Image thumbnail / file preview |
| `frontend/src/components/ImageViewer.tsx` | Full-size image modal |
| `frontend/src/components/EmojiPicker.tsx` | Lazy-loaded emoji picker wrapper |

### Frontend — Modified Files

| File | Changes |
|------|---------|
| `frontend/src/api/types.ts` | Add AttachmentInfo, ReplyPreview, MessageEvent, EditMessageRequest; extend ChatMessage |
| `frontend/src/api/rooms.ts` | Add sendMessageWithAttachments, editMessage, deleteMessage |
| `frontend/src/hooks/useWebSocket.ts` | Subscribe to /events channel, onEvent callback |
| `frontend/src/components/MessageBubble.tsx` | Reply preview, edited indicator, attachments, action buttons, deleted state |
| `frontend/src/components/MessageInput.tsx` | File upload, reply bar, emoji button, file preview |
| `frontend/src/components/ChatArea.tsx` | Drag-drop, onReply/onEdit callbacks, scroll-to-message |
| `frontend/src/pages/ChatLayout.tsx` | replyTo/editing state, events handling, modified send flow |

---

### Task 1: Database Migrations

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/013-create-attachments.sql`
- Create: `backend/src/main/resources/db/changelog/changes/014-add-message-reply-and-edit.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration 013 — attachments table**

Create `backend/src/main/resources/db/changelog/changes/013-create-attachments.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:013-create-attachments
CREATE TABLE attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachments_message_id ON attachments(message_id);
```

- [ ] **Step 2: Create migration 014 — add reply/edit/delete columns to messages**

Create `backend/src/main/resources/db/changelog/changes/014-add-message-reply-and-edit.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:014-add-message-reply-and-edit
ALTER TABLE messages ADD COLUMN reply_to_id UUID REFERENCES messages(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMP;
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_messages_reply_to_id ON messages(reply_to_id);
```

- [ ] **Step 3: Register migrations in changelog**

Add to the end of `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/changes/013-create-attachments.sql
  - include:
      file: db/changelog/changes/014-add-message-reply-and-edit.sql
```

- [ ] **Step 4: Verify migration applies**

Run: `cd backend && ./gradlew bootRun`
Expected: Application starts without Liquibase errors. Stop the app after confirming.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/changelog/changes/013-create-attachments.sql backend/src/main/resources/db/changelog/changes/014-add-message-reply-and-edit.sql backend/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add attachments and message reply/edit/delete migrations"
```

---

### Task 2: Attachment Entity and Extend Message Entity

**Files:**
- Create: `backend/src/main/java/com/chatapp/entity/Attachment.java`
- Create: `backend/src/main/java/com/chatapp/repository/AttachmentRepository.java`
- Modify: `backend/src/main/java/com/chatapp/entity/Message.java`

- [ ] **Step 1: Create Attachment entity**

Create `backend/src/main/java/com/chatapp/entity/Attachment.java`:

```java
package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments")
@Getter
@Setter
@NoArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

}
```

- [ ] **Step 2: Create AttachmentRepository**

Create `backend/src/main/java/com/chatapp/repository/AttachmentRepository.java`:

```java
package com.chatapp.repository;

import com.chatapp.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByMessageId(UUID messageId);

}
```

- [ ] **Step 3: Extend Message entity**

Replace `backend/src/main/java/com/chatapp/entity/Message.java` with:

```java
package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private Message replyTo;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY)
    private List<Attachment> attachments = new ArrayList<>();

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

- [ ] **Step 4: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/Attachment.java backend/src/main/java/com/chatapp/repository/AttachmentRepository.java backend/src/main/java/com/chatapp/entity/Message.java
git commit -m "feat: add Attachment entity and extend Message with reply/edit/delete fields"
```

---

### Task 3: DTOs

**Files:**
- Create: `backend/src/main/java/com/chatapp/dto/EditMessageRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/AttachmentResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/ReplyPreview.java`
- Create: `backend/src/main/java/com/chatapp/dto/MessageEventResponse.java`
- Modify: `backend/src/main/java/com/chatapp/dto/SendMessageRequest.java`
- Modify: `backend/src/main/java/com/chatapp/dto/MessageResponse.java`

- [ ] **Step 1: Create EditMessageRequest**

Create `backend/src/main/java/com/chatapp/dto/EditMessageRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 3000, message = "Message must not exceed 3000 characters")
    private String content;

}
```

- [ ] **Step 2: Create AttachmentResponse**

Create `backend/src/main/java/com/chatapp/dto/AttachmentResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.Attachment;
import lombok.Data;

import java.util.UUID;

@Data
public class AttachmentResponse {

    private UUID id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String downloadUrl;
    private String thumbnailUrl;

    public static AttachmentResponse fromEntity(Attachment attachment) {
        AttachmentResponse response = new AttachmentResponse();
        response.setId(attachment.getId());
        response.setOriginalFileName(attachment.getOriginalFileName());
        response.setContentType(attachment.getContentType());
        response.setFileSize(attachment.getFileSize());
        response.setDownloadUrl("/api/attachments/" + attachment.getId());
        if (attachment.getContentType().startsWith("image/")) {
            response.setThumbnailUrl("/api/attachments/" + attachment.getId() + "/thumbnail");
        }
        return response;
    }

}
```

- [ ] **Step 3: Create ReplyPreview**

Create `backend/src/main/java/com/chatapp/dto/ReplyPreview.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.Message;
import lombok.Data;

import java.util.UUID;

@Data
public class ReplyPreview {

    private UUID messageId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private boolean deleted;

    public static ReplyPreview fromEntity(Message message) {
        ReplyPreview preview = new ReplyPreview();
        preview.setMessageId(message.getId());
        preview.setSenderId(message.getSender().getId());
        preview.setSenderUsername(message.getSender().getUsername());
        preview.setSenderDisplayName(message.getSender().getDisplayName());
        preview.setDeleted(message.getDeletedAt() != null);
        if (!preview.isDeleted()) {
            String text = message.getContent();
            preview.setContent(text.length() > 100 ? text.substring(0, 100) + "..." : text);
        }
        return preview;
    }

}
```

- [ ] **Step 4: Create MessageEventResponse**

Create `backend/src/main/java/com/chatapp/dto/MessageEventResponse.java`:

```java
package com.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MessageEventResponse {

    private String type;
    private Object data;

}
```

- [ ] **Step 5: Extend SendMessageRequest — add replyToId**

Replace `backend/src/main/java/com/chatapp/dto/SendMessageRequest.java` with:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 3000, message = "Message must not exceed 3000 characters")
    private String content;

    private UUID replyToId;

}
```

- [ ] **Step 6: Extend MessageResponse — add reply, edit, delete, attachment fields**

Replace `backend/src/main/java/com/chatapp/dto/MessageResponse.java` with:

```java
package com.chatapp.dto;

import com.chatapp.entity.Attachment;
import com.chatapp.entity.Message;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class MessageResponse {

    private UUID id;
    private UUID roomId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private UUID replyToId;
    private ReplyPreview replyToPreview;
    private Instant editedAt;
    private boolean deleted;
    private List<AttachmentResponse> attachments;
    private Instant createdAt;
    private Instant updatedAt;

    public static MessageResponse fromEntity(Message message) {
        return fromEntity(message, message.getAttachments());
    }

    public static MessageResponse fromEntity(Message message, List<Attachment> attachments) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRoomId(message.getRoom().getId());
        response.setSenderId(message.getSender().getId());
        response.setSenderUsername(message.getSender().getUsername());
        response.setSenderDisplayName(message.getSender().getDisplayName());
        response.setCreatedAt(message.getCreatedAt());
        response.setUpdatedAt(message.getUpdatedAt());
        response.setEditedAt(message.getEditedAt());

        boolean isDeleted = message.getDeletedAt() != null;
        response.setDeleted(isDeleted);

        if (isDeleted) {
            response.setContent(null);
            response.setAttachments(List.of());
            response.setReplyToPreview(null);
        } else {
            response.setContent(message.getContent());
            response.setAttachments(attachments != null
                    ? attachments.stream().map(AttachmentResponse::fromEntity).toList()
                    : List.of());

            if (message.getReplyTo() != null) {
                response.setReplyToId(message.getReplyTo().getId());
                response.setReplyToPreview(ReplyPreview.fromEntity(message.getReplyTo()));
            }
        }

        return response;
    }

}
```

- [ ] **Step 7: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/chatapp/dto/EditMessageRequest.java backend/src/main/java/com/chatapp/dto/AttachmentResponse.java backend/src/main/java/com/chatapp/dto/ReplyPreview.java backend/src/main/java/com/chatapp/dto/MessageEventResponse.java backend/src/main/java/com/chatapp/dto/SendMessageRequest.java backend/src/main/java/com/chatapp/dto/MessageResponse.java
git commit -m "feat: add iteration 5 DTOs and extend MessageResponse"
```

---

### Task 4: Application Configuration

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add multipart and upload config to application.yml**

Add the following to the end of `backend/src/main/resources/application.yml`:

```yaml
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB

chatapp:
  upload-dir: ./uploads
```

The `servlet.multipart` section goes under `spring:`. The `chatapp:` section is top-level.

The full `spring:` block should look like:

```yaml
spring:
  application:
    name: chatapp
  datasource:
    url: jdbc:postgresql://localhost:5432/chatapp
    username: chatapp
    password: chatapp
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  liquibase:
    change-log: classpath:/db/changelog/db.changelog-master.yaml
    enabled: true
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: never
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB

chatapp:
  upload-dir: ./uploads
```

- [ ] **Step 2: Add uploads volume to docker-compose.yml**

Add volume mount to the backend service in `docker-compose.yml`:

```yaml
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: chatapp-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chatapp
      - SPRING_DATASOURCE_USERNAME=chatapp
      - SPRING_DATASOURCE_PASSWORD=chatapp
      - CHATAPP_UPLOAD_DIR=/app/uploads
    volumes:
      - uploads_data:/app/uploads
    depends_on:
      postgres:
        condition: service_healthy
```

Add to the `volumes:` section at the bottom:

```yaml
volumes:
  postgres_data:
  uploads_data:
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application.yml docker-compose.yml
git commit -m "feat: add multipart config and uploads volume"
```

---

### Task 5: FileStorageService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/FileStorageService.java`

- [ ] **Step 1: Create FileStorageService**

Create `backend/src/main/java/com/chatapp/service/FileStorageService.java`:

```java
package com.chatapp.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${chatapp.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    public String store(MultipartFile file) {
        validateFileSize(file);

        String extension = getExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + extension;
        LocalDate now = LocalDate.now();
        String relativePath = now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + storedName;

        Path targetDir = uploadDir.resolve(now.getYear() + "/" + String.format("%02d", now.getMonthValue()));
        try {
            Files.createDirectories(targetDir);
            Path targetPath = uploadDir.resolve(relativePath);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        return relativePath;
    }

    public Resource load(String storagePath) {
        try {
            Path filePath = uploadDir.resolve(storagePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw new RuntimeException("File not found: " + storagePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + storagePath, e);
        }
    }

    public void delete(String storagePath) {
        try {
            Path filePath = uploadDir.resolve(storagePath).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", storagePath, e);
        }
    }

    private void validateFileSize(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/") && file.getSize() > 3 * 1024 * 1024) {
            throw new IllegalArgumentException("Image files must not exceed 3MB");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/FileStorageService.java
git commit -m "feat: add FileStorageService for local filesystem storage"
```

---

### Task 6: AttachmentService

**Files:**
- Create: `backend/src/main/java/com/chatapp/service/AttachmentService.java`

- [ ] **Step 1: Create AttachmentService**

Create `backend/src/main/java/com/chatapp/service/AttachmentService.java`:

```java
package com.chatapp.service;

import com.chatapp.entity.Attachment;
import com.chatapp.entity.Message;
import com.chatapp.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public List<Attachment> createAttachments(Message message, List<MultipartFile> files) {
        if (files.size() > 5) {
            throw new IllegalArgumentException("Maximum 5 files per message");
        }

        List<Attachment> attachments = new ArrayList<>();
        for (MultipartFile file : files) {
            String storagePath = fileStorageService.store(file);

            Attachment attachment = new Attachment();
            attachment.setMessage(message);
            attachment.setFileName(storagePath.substring(storagePath.lastIndexOf('/') + 1));
            attachment.setOriginalFileName(file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setStoragePath(storagePath);
            attachments.add(attachmentRepository.save(attachment));
        }
        return attachments;
    }

    public Attachment getAttachment(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
    }

    public List<Attachment> getAttachmentsByMessageId(UUID messageId) {
        return attachmentRepository.findByMessageId(messageId);
    }

}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/AttachmentService.java
git commit -m "feat: add AttachmentService"
```

---

### Task 7: Extend MessageService

**Files:**
- Modify: `backend/src/main/java/com/chatapp/service/MessageService.java`

- [ ] **Step 1: Add edit, delete, reply support to MessageService**

Replace `backend/src/main/java/com/chatapp/service/MessageService.java` with:

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
        return sendMessage(room, sender, content, null);
    }

    @Transactional
    public Message sendMessage(ChatRoom room, User sender, String content, UUID replyToId) {
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

        if (replyToId != null) {
            Message replyTo = messageRepository.findById(replyToId)
                    .orElseThrow(() -> new IllegalArgumentException("Reply-to message not found"));
            if (!replyTo.getRoom().getId().equals(room.getId())) {
                throw new IllegalArgumentException("Reply-to message must be in the same room");
            }
            message.setReplyTo(replyTo);
        }

        return messageRepository.save(message);
    }

    @Transactional
    public Message editMessage(UUID messageId, Long userId, String newContent) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new IllegalStateException("Only the sender can edit a message");
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot edit a deleted message");
        }

        message.setContent(newContent);
        message.setEditedAt(Instant.now());
        return messageRepository.save(message);
    }

    @Transactional
    public Message deleteMessage(UUID messageId, Long userId, UUID roomId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("Message does not belong to this room");
        }

        boolean isSender = message.getSender().getId().equals(userId);
        boolean isOwner = message.getRoom().getOwner().getId().equals(userId);

        if (!isSender && !isOwner) {
            throw new IllegalStateException("Only the sender or room owner can delete a message");
        }

        message.setDeletedAt(Instant.now());
        return messageRepository.save(message);
    }

    public Message findById(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
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

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/MessageService.java
git commit -m "feat: extend MessageService with edit, delete, and reply support"
```

---

### Task 8: MessageController and Update ChatRoomController

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/MessageController.java`
- Modify: `backend/src/main/java/com/chatapp/controller/ChatRoomController.java`

- [ ] **Step 1: Create MessageController**

Create `backend/src/main/java/com/chatapp/controller/MessageController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.*;
import com.chatapp.entity.Attachment;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.service.AttachmentService;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final UserService userService;
    private final AttachmentService attachmentService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> postMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        User sender = userService.findById(userId);
        ChatRoom room = chatRoomService.findById(roomId);
        Message message = messageService.sendMessage(room, sender, request.getContent(), request.getReplyToId());
        MessageResponse response = MessageResponse.fromEntity(message, List.of());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/messages", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> postMessageWithAttachments(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) UUID replyToId,
            @RequestPart(required = false) List<MultipartFile> files,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        if ((content == null || content.isBlank()) && (files == null || files.isEmpty())) {
            throw new IllegalArgumentException("At least content or files required");
        }

        chatRoomService.validateMembership(roomId, userId);
        User sender = userService.findById(userId);
        ChatRoom room = chatRoomService.findById(roomId);

        String messageContent = (content != null && !content.isBlank()) ? content : "";
        Message message = messageService.sendMessage(room, sender, messageContent, replyToId);

        List<Attachment> savedAttachments = List.of();
        if (files != null && !files.isEmpty()) {
            savedAttachments = attachmentService.createAttachments(message, files);
        }

        MessageResponse response = MessageResponse.fromEntity(message, savedAttachments);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/messages", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Instant before,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        int cappedLimit = Math.min(limit, 100);
        List<MessageResponse> messages = messageService.getMessages(roomId, before, cappedLimit).stream()
                .map(MessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        Message message = messageService.editMessage(messageId, userId, request.getContent());
        MessageResponse response = MessageResponse.fromEntity(message);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/events",
                new MessageEventResponse("MESSAGE_EDITED", response));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        messageService.deleteMessage(messageId, userId, roomId);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/events",
                new MessageEventResponse("MESSAGE_DELETED", Map.of("messageId", messageId)));
        return ResponseEntity.ok().build();
    }

}
```

- [ ] **Step 2: Remove message endpoints from ChatRoomController**

Remove the `postMessage` and `getMessages` methods from `backend/src/main/java/com/chatapp/controller/ChatRoomController.java`. Also remove the `MessageService`, `UserService`, and `SimpMessagingTemplate` fields if they are no longer used by other methods in the controller. Remove unused imports.

The removed methods are:
```java
// REMOVE this method:
@PostMapping("/{roomId}/messages")
public ResponseEntity<MessageResponse> postMessage(...)

// REMOVE this method:
@GetMapping("/{roomId}/messages")
public ResponseEntity<List<MessageResponse>> getMessages(...)
```

Remove the corresponding fields and imports that are no longer needed:
- Remove `MessageService messageService` field
- Remove `UserService userService` field
- Remove `SimpMessagingTemplate messagingTemplate` field
- Remove unused imports: `SendMessageRequest`, `MessageResponse`, `Message`, `User`, `SimpMessagingTemplate`, `Instant`

The remaining ChatRoomController should only have room CRUD, join/leave, and members endpoints with `ChatRoomService` as its only dependency.

- [ ] **Step 3: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run existing tests to check for regressions**

Run: `cd backend && ./gradlew test`
Expected: All existing tests PASS (the tests POST to `/api/rooms/{roomId}/messages` with JSON content type, which now routes to MessageController)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/MessageController.java backend/src/main/java/com/chatapp/controller/ChatRoomController.java
git commit -m "feat: add MessageController with multipart upload, edit, and delete"
```

---

### Task 9: AttachmentController

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/AttachmentController.java`

- [ ] **Step 1: Create AttachmentController**

Create `backend/src/main/java/com/chatapp/controller/AttachmentController.java`:

```java
package com.chatapp.controller;

import com.chatapp.entity.Attachment;
import com.chatapp.service.AttachmentService;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ChatRoomService chatRoomService;
    private final FileStorageService fileStorageService;

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID attachmentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Attachment attachment = attachmentService.getAttachment(attachmentId);

        chatRoomService.validateMembership(attachment.getMessage().getRoom().getId(), userId);

        if (attachment.getMessage().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileStorageService.load(attachment.getStoragePath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/{attachmentId}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(
            @PathVariable UUID attachmentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Attachment attachment = attachmentService.getAttachment(attachmentId);

        chatRoomService.validateMembership(attachment.getMessage().getRoom().getId(), userId);

        if (attachment.getMessage().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        if (!attachment.getContentType().startsWith("image/")) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileStorageService.load(attachment.getStoragePath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + attachment.getOriginalFileName() + "\"")
                .body(resource);
    }

}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/AttachmentController.java
git commit -m "feat: add AttachmentController for file download and thumbnail"
```

---

### Task 10: Extend WebSocketMessageController

**Files:**
- Modify: `backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java`

- [ ] **Step 1: Update WebSocketMessageController to support replyToId and build full response**

Replace `backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java` with:

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

import java.util.List;
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
        Message message = messageService.sendMessage(room, sender, request.getContent(), request.getReplyToId());

        MessageResponse response = MessageResponse.fromEntity(message, List.of());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/messages", response);
    }

}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/WebSocketMessageController.java
git commit -m "feat: extend WebSocketMessageController with replyToId support"
```

---

### Task 11: Frontend Dependencies and Types

**Files:**
- Modify: `frontend/src/api/types.ts`

- [ ] **Step 1: Install emoji picker dependencies**

Run: `cd frontend && npm install @emoji-mart/react @emoji-mart/data`
Expected: Packages installed successfully

- [ ] **Step 2: Extend types.ts with new types and updated ChatMessage**

Add the following types to the end of `frontend/src/api/types.ts`:

```typescript
export interface AttachmentInfo {
  id: string;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  downloadUrl: string;
  thumbnailUrl: string | null;
}

export interface ReplyPreview {
  messageId: string;
  senderId: number;
  senderUsername: string;
  senderDisplayName: string | null;
  content: string | null;
  deleted: boolean;
}

export interface MessageEvent {
  type: 'MESSAGE_EDITED' | 'MESSAGE_DELETED';
  data: ChatMessage | { messageId: string };
}

export interface EditMessageRequest {
  content: string;
}
```

Also update the existing `ChatMessage` interface to add new fields:

```typescript
export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: number;
  senderUsername: string;
  senderDisplayName: string | null;
  content: string | null;
  replyToId: string | null;
  replyToPreview: ReplyPreview | null;
  editedAt: string | null;
  deleted: boolean;
  attachments: AttachmentInfo[];
  createdAt: string;
  updatedAt: string;
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/api/types.ts
git commit -m "feat: add frontend dependencies and types for attachments and rich messaging"
```

---

### Task 12: Frontend API Layer

**Files:**
- Modify: `frontend/src/api/rooms.ts`
- Create: `frontend/src/api/attachments.ts`

- [ ] **Step 1: Extend rooms.ts with message operations**

Add the following methods to the `roomsApi` object in `frontend/src/api/rooms.ts`:

```typescript
  sendMessageWithAttachments: async (
    roomId: string,
    content: string | null,
    files: File[],
    replyToId?: string
  ): Promise<ChatMessage> => {
    const formData = new FormData();
    if (content) formData.append('content', content);
    if (replyToId) formData.append('replyToId', replyToId);
    files.forEach(file => formData.append('files', file));
    const { data } = await client.post<ChatMessage>(`/rooms/${roomId}/messages`, formData);
    return data;
  },

  editMessage: async (roomId: string, messageId: string, content: string): Promise<ChatMessage> => {
    const { data } = await client.put<ChatMessage>(`/rooms/${roomId}/messages/${messageId}`, { content });
    return data;
  },

  deleteMessage: async (roomId: string, messageId: string): Promise<void> => {
    await client.delete(`/rooms/${roomId}/messages/${messageId}`);
  },
```

Also update the `sendMessage` method to support `replyToId`:

```typescript
  sendMessage: async (roomId: string, content: string, replyToId?: string): Promise<ChatMessage> => {
    const { data } = await client.post<ChatMessage>(`/rooms/${roomId}/messages`, { content, replyToId });
    return data;
  },
```

- [ ] **Step 2: Create attachments.ts**

Create `frontend/src/api/attachments.ts`:

```typescript
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const attachmentApi = {
  getDownloadUrl: (attachmentId: string): string => {
    return `${API_BASE}/attachments/${attachmentId}`;
  },

  getThumbnailUrl: (attachmentId: string): string => {
    return `${API_BASE}/attachments/${attachmentId}/thumbnail`;
  },
};
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/rooms.ts frontend/src/api/attachments.ts
git commit -m "feat: add attachment API and extend rooms API with edit/delete/upload"
```

---

### Task 13: Extend useWebSocket with Events Subscription

**Files:**
- Modify: `frontend/src/hooks/useWebSocket.ts`

- [ ] **Step 1: Extend useWebSocket to subscribe to events channel**

Replace `frontend/src/hooks/useWebSocket.ts` with:

```typescript
import { useRef, useCallback, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';
import type { ChatMessage, PresenceEvent, MessageEvent } from '../api/types.ts';

const WS_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/api$/, '').replace(/^http/, 'ws') + '/ws';

interface UseWebSocketOptions {
  onMessage: (roomId: string, message: ChatMessage) => void;
  onPresence?: (event: PresenceEvent) => void;
  onEvent?: (roomId: string, event: MessageEvent) => void;
}

export function useWebSocket({ onMessage, onPresence, onEvent }: UseWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }[]>>(new Map());
  const pendingSubscriptionsRef = useRef<Set<string>>(new Set());
  const onMessageRef = useRef(onMessage);
  const onPresenceRef = useRef(onPresence);
  const onEventRef = useRef(onEvent);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  onMessageRef.current = onMessage;
  onPresenceRef.current = onPresence;
  onEventRef.current = onEvent;

  const doSubscribe = useCallback((client: Client, roomId: string) => {
    if (subscriptionsRef.current.has(roomId)) return;

    const messageSub = client.subscribe(`/topic/rooms/${roomId}/messages`, (msg: IMessage) => {
      const message: ChatMessage = JSON.parse(msg.body);
      onMessageRef.current(roomId, message);
    });

    const eventSub = client.subscribe(`/topic/rooms/${roomId}/events`, (msg: IMessage) => {
      const event: MessageEvent = JSON.parse(msg.body);
      onEventRef.current?.(roomId, event);
    });

    subscriptionsRef.current.set(roomId, [messageSub, eventSub]);
  }, []);

  useEffect(() => {
    const stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        pendingSubscriptionsRef.current.forEach(roomId => {
          doSubscribe(stompClient, roomId);
        });
        pendingSubscriptionsRef.current.clear();

        stompClient.subscribe('/topic/presence', (msg: IMessage) => {
          const event: PresenceEvent = JSON.parse(msg.body);
          onPresenceRef.current?.(event);
        });

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
      subscriptionsRef.current.forEach(subs => subs.forEach(sub => sub.unsubscribe()));
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
    const subs = subscriptionsRef.current.get(roomId);
    if (subs) {
      subs.forEach(sub => sub.unsubscribe());
      subscriptionsRef.current.delete(roomId);
    }
  }, []);

  const sendMessage = useCallback((roomId: string, content: string, replyToId?: string) => {
    const client = clientRef.current;
    if (!client?.connected) return;
    client.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify({ content, replyToId }),
    });
  }, []);

  return { subscribe, unsubscribe, sendMessage };
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/hooks/useWebSocket.ts
git commit -m "feat: extend useWebSocket with events subscription and replyToId"
```

---

### Task 14: New Components — AttachmentPreview, ImageViewer, EmojiPicker

**Files:**
- Create: `frontend/src/components/AttachmentPreview.tsx`
- Create: `frontend/src/components/ImageViewer.tsx`
- Create: `frontend/src/components/EmojiPicker.tsx`

- [ ] **Step 1: Create AttachmentPreview**

Create `frontend/src/components/AttachmentPreview.tsx`:

```typescript
import { FileOutlined, DownloadOutlined } from '@ant-design/icons';
import type { AttachmentInfo } from '../api/types.ts';
import { attachmentApi } from '../api/attachments.ts';

interface Props {
  attachment: AttachmentInfo;
  onImageClick?: () => void;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

export const AttachmentPreview = ({ attachment, onImageClick }: Props) => {
  const isImage = attachment.contentType.startsWith('image/');

  if (isImage && attachment.thumbnailUrl) {
    return (
      <div
        onClick={onImageClick}
        style={{
          cursor: 'pointer',
          marginTop: 4,
          borderRadius: 8,
          overflow: 'hidden',
          display: 'inline-block',
        }}
      >
        <img
          src={attachmentApi.getThumbnailUrl(attachment.id)}
          alt={attachment.originalFileName}
          style={{ maxWidth: 200, maxHeight: 200, display: 'block' }}
        />
      </div>
    );
  }

  return (
    <a
      href={attachmentApi.getDownloadUrl(attachment.id)}
      target="_blank"
      rel="noopener noreferrer"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '6px 10px',
        marginTop: 4,
        borderRadius: 6,
        background: 'rgba(0,0,0,0.04)',
        color: '#1677ff',
        textDecoration: 'none',
        fontSize: 13,
      }}
    >
      <FileOutlined />
      <span style={{ maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {attachment.originalFileName}
      </span>
      <span style={{ color: 'rgba(0,0,0,0.4)', fontSize: 11 }}>
        {formatFileSize(attachment.fileSize)}
      </span>
      <DownloadOutlined />
    </a>
  );
};
```

- [ ] **Step 2: Create ImageViewer**

Create `frontend/src/components/ImageViewer.tsx`:

```typescript
import { Modal, Button } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { AttachmentInfo } from '../api/types.ts';
import { attachmentApi } from '../api/attachments.ts';

interface Props {
  attachment: AttachmentInfo | null;
  onClose: () => void;
}

export const ImageViewer = ({ attachment, onClose }: Props) => {
  if (!attachment) return null;

  return (
    <Modal
      open={!!attachment}
      onCancel={onClose}
      footer={[
        <Button
          key="download"
          icon={<DownloadOutlined />}
          href={attachmentApi.getDownloadUrl(attachment.id)}
          target="_blank"
        >
          Download
        </Button>,
      ]}
      width="80vw"
      centered
    >
      <img
        src={attachmentApi.getDownloadUrl(attachment.id)}
        alt={attachment.originalFileName}
        style={{ width: '100%', maxHeight: '70vh', objectFit: 'contain' }}
      />
    </Modal>
  );
};
```

- [ ] **Step 3: Create EmojiPicker**

Create `frontend/src/components/EmojiPicker.tsx`:

```typescript
import { Suspense, lazy } from 'react';
import { Popover, Button, Spin } from 'antd';
import { SmileOutlined } from '@ant-design/icons';

const Picker = lazy(() => import('@emoji-mart/react').then(mod => ({ default: mod.default })));

interface Props {
  onSelect: (emoji: string) => void;
}

export const EmojiPicker = ({ onSelect }: Props) => {
  const handleSelect = (emoji: { native: string }) => {
    onSelect(emoji.native);
  };

  return (
    <Popover
      trigger="click"
      placement="topRight"
      content={
        <Suspense fallback={<Spin size="small" />}>
          <Picker
            data={async () => (await import('@emoji-mart/data')).default}
            onEmojiSelect={handleSelect}
            theme="light"
            previewPosition="none"
            skinTonePosition="none"
          />
        </Suspense>
      }
    >
      <Button type="text" icon={<SmileOutlined />} />
    </Popover>
  );
};
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/AttachmentPreview.tsx frontend/src/components/ImageViewer.tsx frontend/src/components/EmojiPicker.tsx
git commit -m "feat: add AttachmentPreview, ImageViewer, and EmojiPicker components"
```

---

### Task 15: Extend MessageBubble

**Files:**
- Modify: `frontend/src/components/MessageBubble.tsx`

- [ ] **Step 1: Update MessageBubble with reply preview, edited indicator, attachments, and actions**

Replace `frontend/src/components/MessageBubble.tsx` with:

```typescript
import { useState } from 'react';
import { EditOutlined, DeleteOutlined, MessageOutlined } from '@ant-design/icons';
import { Popconfirm } from 'antd';
import type { ChatMessage, AttachmentInfo } from '../api/types.ts';
import { AttachmentPreview } from './AttachmentPreview.tsx';
import { ImageViewer } from './ImageViewer.tsx';

interface MessageBubbleProps {
  message: ChatMessage;
  isOwn: boolean;
  isRoomOwner: boolean;
  onReply?: (message: ChatMessage) => void;
  onEdit?: (message: ChatMessage) => void;
  onDelete?: (messageId: string) => void;
  onReplyClick?: (messageId: string) => void;
}

export const MessageBubble = ({ message, isOwn, isRoomOwner, onReply, onEdit, onDelete, onReplyClick }: MessageBubbleProps) => {
  const [hovered, setHovered] = useState(false);
  const [viewingImage, setViewingImage] = useState<AttachmentInfo | null>(null);

  const time = new Date(message.createdAt).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  if (message.deleted) {
    return (
      <div style={{ display: 'flex', justifyContent: isOwn ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
        <div style={{
          maxWidth: '70%',
          padding: '8px 12px',
          borderRadius: 12,
          background: '#f5f5f5',
          color: 'rgba(0,0,0,0.3)',
          fontStyle: 'italic',
          fontSize: 13,
        }}>
          This message was deleted
        </div>
      </div>
    );
  }

  const showActions = hovered && !message.deleted;
  const canEdit = isOwn && onEdit;
  const canDelete = (isOwn || isRoomOwner) && onDelete;

  return (
    <div
      style={{ display: 'flex', justifyContent: isOwn ? 'flex-end' : 'flex-start', marginBottom: 8, position: 'relative' }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <div style={{
        maxWidth: '70%',
        padding: '8px 12px',
        borderRadius: 12,
        background: isOwn ? '#1677ff' : '#f0f0f0',
        color: isOwn ? '#fff' : '#000',
      }}>
        {!isOwn && (
          <div style={{ fontSize: 12, color: '#1677ff', fontWeight: 500, marginBottom: 2 }}>
            {message.senderDisplayName || message.senderUsername}
          </div>
        )}

        {message.replyToPreview && (
          <div
            onClick={() => message.replyToPreview && onReplyClick?.(message.replyToPreview.messageId)}
            style={{
              padding: '4px 8px',
              marginBottom: 4,
              borderLeft: '3px solid ' + (isOwn ? 'rgba(255,255,255,0.5)' : '#1677ff'),
              borderRadius: 4,
              background: isOwn ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.05)',
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            <div style={{ fontWeight: 500, opacity: 0.8 }}>
              {message.replyToPreview.senderDisplayName || message.replyToPreview.senderUsername}
            </div>
            <div style={{ opacity: 0.7 }}>
              {message.replyToPreview.deleted ? '[deleted message]' : message.replyToPreview.content}
            </div>
          </div>
        )}

        {message.content && (
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {message.content}
          </div>
        )}

        {message.attachments && message.attachments.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: message.content ? 4 : 0 }}>
            {message.attachments.map(att => (
              <AttachmentPreview
                key={att.id}
                attachment={att}
                onImageClick={() => att.contentType.startsWith('image/') && setViewingImage(att)}
              />
            ))}
          </div>
        )}

        <div style={{
          fontSize: 11,
          color: isOwn ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.4)',
          textAlign: 'right',
          marginTop: 4,
        }}>
          {message.editedAt && <span style={{ marginRight: 4 }}>(edited)</span>}
          {time}
        </div>
      </div>

      {showActions && (
        <div style={{
          display: 'flex',
          gap: 2,
          position: 'absolute',
          top: -8,
          [isOwn ? 'left' : 'right']: 0,
          background: '#fff',
          borderRadius: 4,
          boxShadow: '0 1px 3px rgba(0,0,0,0.15)',
          padding: '2px 4px',
        }}>
          {onReply && (
            <MessageOutlined
              onClick={() => onReply(message)}
              style={{ fontSize: 14, cursor: 'pointer', padding: 4, color: '#666' }}
            />
          )}
          {canEdit && (
            <EditOutlined
              onClick={() => onEdit!(message)}
              style={{ fontSize: 14, cursor: 'pointer', padding: 4, color: '#666' }}
            />
          )}
          {canDelete && (
            <Popconfirm title="Delete this message?" onConfirm={() => onDelete!(message.id)} okText="Delete" okType="danger">
              <DeleteOutlined style={{ fontSize: 14, cursor: 'pointer', padding: 4, color: '#ff4d4f' }} />
            </Popconfirm>
          )}
        </div>
      )}

      <ImageViewer attachment={viewingImage} onClose={() => setViewingImage(null)} />
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/MessageBubble.tsx
git commit -m "feat: extend MessageBubble with reply preview, edit/delete actions, and attachments"
```

---

### Task 16: Extend MessageInput

**Files:**
- Modify: `frontend/src/components/MessageInput.tsx`

- [ ] **Step 1: Update MessageInput with file upload, reply bar, and emoji**

Replace `frontend/src/components/MessageInput.tsx` with:

```typescript
import { useState, useRef } from 'react';
import { Input, Button, Tag } from 'antd';
import { SendOutlined, PaperClipOutlined, CloseOutlined } from '@ant-design/icons';
import { EmojiPicker } from './EmojiPicker.tsx';
import type { ChatMessage } from '../api/types.ts';

interface MessageInputProps {
  onSend: (content: string, files: File[], replyToId?: string) => void;
  onCancelReply?: () => void;
  onCancelEdit?: () => void;
  replyTo?: ChatMessage | null;
  editingMessage?: ChatMessage | null;
  disabled?: boolean;
}

export const MessageInput = ({ onSend, onCancelReply, onCancelEdit, replyTo, editingMessage, disabled }: MessageInputProps) => {
  const [value, setValue] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textAreaRef = useRef<HTMLTextAreaElement>(null);

  // Populate input when editing
  const isEditing = !!editingMessage;
  if (isEditing && value === '' && editingMessage.content) {
    setValue(editingMessage.content);
  }

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed && files.length === 0) return;

    if (isEditing) {
      onSend(trimmed, [], undefined);
    } else {
      onSend(trimmed, files, replyTo?.id);
    }

    setValue('');
    setFiles([]);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      addFiles(Array.from(e.target.files));
      e.target.value = '';
    }
  };

  const addFiles = (newFiles: File[]) => {
    setFiles(prev => {
      const combined = [...prev, ...newFiles];
      if (combined.length > 5) {
        return combined.slice(0, 5);
      }
      return combined;
    });
  };

  const removeFile = (index: number) => {
    setFiles(prev => prev.filter((_, i) => i !== index));
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    const pastedFiles = Array.from(e.clipboardData.files);
    if (pastedFiles.length > 0) {
      e.preventDefault();
      addFiles(pastedFiles);
    }
  };

  const handleEmojiSelect = (emoji: string) => {
    setValue(prev => prev + emoji);
    textAreaRef.current?.focus();
  };

  return (
    <div style={{ borderTop: '1px solid #f0f0f0' }}>
      {replyTo && !isEditing && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '6px 16px',
          background: '#f5f5f5',
          fontSize: 12,
          color: '#666',
        }}>
          <span>
            Replying to <strong>{replyTo.senderDisplayName || replyTo.senderUsername}</strong>:{' '}
            {replyTo.content?.substring(0, 50)}{(replyTo.content?.length || 0) > 50 ? '...' : ''}
          </span>
          <CloseOutlined onClick={onCancelReply} style={{ cursor: 'pointer', fontSize: 12 }} />
        </div>
      )}

      {isEditing && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '6px 16px',
          background: '#fff7e6',
          fontSize: 12,
          color: '#d48806',
        }}>
          <span>Editing message</span>
          <CloseOutlined onClick={onCancelEdit} style={{ cursor: 'pointer', fontSize: 12 }} />
        </div>
      )}

      {files.length > 0 && (
        <div style={{ display: 'flex', gap: 4, padding: '6px 16px', flexWrap: 'wrap' }}>
          {files.map((file, i) => (
            <Tag key={i} closable onClose={() => removeFile(i)}>
              {file.name.length > 20 ? file.name.substring(0, 20) + '...' : file.name}
            </Tag>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', gap: 8, padding: '12px 16px', alignItems: 'flex-end' }}>
        {!isEditing && (
          <>
            <Button
              type="text"
              icon={<PaperClipOutlined />}
              onClick={() => fileInputRef.current?.click()}
              disabled={disabled || files.length >= 5}
            />
            <input
              ref={fileInputRef}
              type="file"
              multiple
              style={{ display: 'none' }}
              onChange={handleFileSelect}
            />
          </>
        )}
        <EmojiPicker onSelect={handleEmojiSelect} />
        <Input.TextArea
          ref={textAreaRef as React.Ref<any>}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          placeholder={isEditing ? 'Edit message...' : 'Type a message...'}
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={disabled}
          style={{ flex: 1 }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          disabled={(!value.trim() && files.length === 0) || disabled}
        />
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/MessageInput.tsx
git commit -m "feat: extend MessageInput with file upload, reply bar, and emoji picker"
```

---

### Task 17: Extend ChatArea and ChatLayout

**Files:**
- Modify: `frontend/src/components/ChatArea.tsx`
- Modify: `frontend/src/pages/ChatLayout.tsx`

- [ ] **Step 1: Update ChatArea with drag-drop and message action callbacks**

Replace `frontend/src/components/ChatArea.tsx` with:

```typescript
import { useState, useEffect, useRef, useCallback } from 'react';
import { Spin, Typography } from 'antd';
import { MessageBubble } from './MessageBubble.tsx';
import type { ChatMessage } from '../api/types.ts';

const { Text } = Typography;

interface ChatAreaProps {
  messages: ChatMessage[];
  currentUserId: number;
  roomOwnerId: number;
  onLoadMore: () => Promise<boolean>;
  loading?: boolean;
  onReply: (message: ChatMessage) => void;
  onEdit: (message: ChatMessage) => void;
  onDelete: (messageId: string) => void;
  onFileDrop: (files: File[]) => void;
}

export const ChatArea = ({ messages, currentUserId, roomOwnerId, onLoadMore, loading, onReply, onEdit, onDelete, onFileDrop }: ChatAreaProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);
  const prevMessagesLengthRef = useRef(0);
  const loadingRef = useRef(false);
  const [dragging, setDragging] = useState(false);

  const checkIfAtBottom = () => {
    const el = containerRef.current;
    if (!el) return;
    isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
  };

  const scrollToBottom = () => {
    const el = containerRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  };

  const scrollToMessage = (messageId: string) => {
    const el = document.getElementById(`msg-${messageId}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.style.transition = 'background 0.3s';
      el.style.background = '#fff7e6';
      setTimeout(() => { el.style.background = 'transparent'; }, 1500);
    }
  };

  useEffect(() => {
    if (messages.length > prevMessagesLengthRef.current) {
      const addedToEnd = prevMessagesLengthRef.current === 0 || isAtBottomRef.current;
      if (addedToEnd) {
        scrollToBottom();
      }
    }
    prevMessagesLengthRef.current = messages.length;
  }, [messages]);

  const handleScroll = useCallback(async () => {
    checkIfAtBottom();
    const el = containerRef.current;
    if (!el || loadingRef.current) return;
    if (el.scrollTop < 100) {
      loadingRef.current = true;
      const prevScrollHeight = el.scrollHeight;
      try {
        await onLoadMore();
        requestAnimationFrame(() => {
          el.scrollTop = el.scrollHeight - prevScrollHeight;
        });
      } finally {
        loadingRef.current = false;
      }
    }
  }, [onLoadMore]);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    if (e.currentTarget === e.target) {
      setDragging(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const droppedFiles = Array.from(e.dataTransfer.files);
    if (droppedFiles.length > 0) {
      onFileDrop(droppedFiles);
    }
  };

  if (messages.length === 0 && !loading) {
    return (
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'rgba(0,0,0,0.4)',
          position: 'relative',
        }}
      >
        <Text type="secondary">No messages yet. Start the conversation!</Text>
        {dragging && <DropOverlay />}
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: '16px',
        position: 'relative',
      }}
    >
      {loading && (
        <div style={{ textAlign: 'center', padding: 8 }}>
          <Spin size="small" />
        </div>
      )}
      {messages.map((msg) => (
        <div key={msg.id} id={`msg-${msg.id}`}>
          <MessageBubble
            message={msg}
            isOwn={msg.senderId === currentUserId}
            isRoomOwner={roomOwnerId === currentUserId}
            onReply={onReply}
            onEdit={onEdit}
            onDelete={onDelete}
            onReplyClick={scrollToMessage}
          />
        </div>
      ))}
      {dragging && <DropOverlay />}
    </div>
  );
};

const DropOverlay = () => (
  <div style={{
    position: 'absolute',
    inset: 0,
    background: 'rgba(22, 119, 255, 0.08)',
    border: '2px dashed #1677ff',
    borderRadius: 8,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 10,
    pointerEvents: 'none',
  }}>
    <Text style={{ fontSize: 16, color: '#1677ff' }}>Drop files here</Text>
  </div>
);
```

- [ ] **Step 2: Update ChatLayout with reply/edit state and events handling**

Replace `frontend/src/pages/ChatLayout.tsx` with:

```typescript
import { useState, useEffect, useCallback, useRef } from 'react';
import type { ChatRoom, ChatMessage, MessageEvent } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { presenceApi } from '../api/presence.ts';
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
  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null);
  const [editingMessage, setEditingMessage] = useState<ChatMessage | null>(null);
  const [droppedFiles, setDroppedFiles] = useState<File[]>([]);

  const { getPresence, handlePresenceEvent } = usePresence();
  const { getUnreadCount, incrementUnread, markAsRead } = useUnread();

  const selectedRoomRef = useRef<ChatRoom | null>(null);
  useEffect(() => { selectedRoomRef.current = selectedRoom; }, [selectedRoom]);

  const handleNewMessage = useCallback((roomId: string, msg: ChatMessage) => {
    setMessages(prev => {
      const next = new Map(prev);
      const existing = next.get(roomId) || [];
      if (existing.some(m => m.id === msg.id)) return prev;
      next.set(roomId, [...existing, msg]);
      return next;
    });

    if (!selectedRoomRef.current || selectedRoomRef.current.id !== roomId) {
      incrementUnread(roomId);
    }
  }, [incrementUnread]);

  const handleEvent = useCallback((roomId: string, event: MessageEvent) => {
    if (event.type === 'MESSAGE_EDITED') {
      const updated = event.data as ChatMessage;
      setMessages(prev => {
        const next = new Map(prev);
        const existing = next.get(roomId) || [];
        next.set(roomId, existing.map(m => m.id === updated.id ? updated : m));
        return next;
      });
    } else if (event.type === 'MESSAGE_DELETED') {
      const { messageId } = event.data as { messageId: string };
      setMessages(prev => {
        const next = new Map(prev);
        const existing = next.get(roomId) || [];
        next.set(roomId, existing.map(m =>
          m.id === messageId ? { ...m, deleted: true, content: null, attachments: [] } : m
        ));
        return next;
      });
    }
  }, []);

  const { subscribe, unsubscribe, sendMessage } = useWebSocket({
    onMessage: handleNewMessage,
    onPresence: handlePresenceEvent,
    onEvent: handleEvent,
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
    setReplyTo(null);
    setEditingMessage(null);

    try {
      const msgs = await roomsApi.getMessages(room.id);
      if (msgs.length > 0) {
        markAsRead(room.id, msgs[0].id);
      }
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

    presenceApi.getRoomMemberPresence(room.id)
      .then(presences => presences.forEach(handlePresenceEvent))
      .catch(console.error);
  }, [selectedRoom, subscribe, unsubscribe, markAsRead, handlePresenceEvent]);

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

  const handleSendMessage = useCallback(async (content: string, files: File[], replyToId?: string) => {
    if (!selectedRoom) return;

    if (editingMessage) {
      try {
        await roomsApi.editMessage(selectedRoom.id, editingMessage.id, content);
      } catch (err) {
        console.error('Failed to edit message', err);
      }
      setEditingMessage(null);
      return;
    }

    try {
      if (files.length > 0) {
        const msg = await roomsApi.sendMessageWithAttachments(selectedRoom.id, content || null, files, replyToId);
        handleNewMessage(selectedRoom.id, msg);
      } else if (replyToId) {
        const msg = await roomsApi.sendMessage(selectedRoom.id, content, replyToId);
        handleNewMessage(selectedRoom.id, msg);
      } else {
        sendMessage(selectedRoom.id, content);
      }
    } catch (err) {
      console.error('Failed to send message', err);
    }

    setReplyTo(null);
  }, [selectedRoom, editingMessage, sendMessage, handleNewMessage]);

  const handleDeleteMessage = useCallback(async (messageId: string) => {
    if (!selectedRoom) return;
    try {
      await roomsApi.deleteMessage(selectedRoom.id, messageId);
    } catch (err) {
      console.error('Failed to delete message', err);
    }
  }, [selectedRoom]);

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

  const handleFileDrop = useCallback((files: File[]) => {
    setDroppedFiles(files);
  }, []);

  const getDmPresence = () => {
    if (!selectedRoom || selectedRoom.type !== 'DIRECT') return undefined;
    const dmMatch = selectedRoom.name.match(/^dm-(\d+)-(\d+)$/);
    if (!dmMatch) return undefined;
    const id1 = Number(dmMatch[1]);
    const id2 = Number(dmMatch[2]);
    const otherUserId = id1 === user!.id ? id2 : id1;
    return getPresence(otherUserId);
  };

  const currentMessages = selectedRoom ? (messages.get(selectedRoom.id) || []) : [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
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
              <ChatArea
                messages={currentMessages}
                currentUserId={user!.id}
                roomOwnerId={selectedRoom.ownerId}
                onLoadMore={handleLoadMore}
                loading={loadingMessages}
                onReply={setReplyTo}
                onEdit={setEditingMessage}
                onDelete={handleDeleteMessage}
                onFileDrop={handleFileDrop}
              />
              <MessageInput
                onSend={handleSendMessage}
                replyTo={replyTo}
                onCancelReply={() => setReplyTo(null)}
                editingMessage={editingMessage}
                onCancelEdit={() => setEditingMessage(null)}
              />
            </>
          ) : (
            <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', color: '#999' }}>
              Select a room to start chatting
            </div>
          )}
        </div>
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

- [ ] **Step 3: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/ChatArea.tsx frontend/src/pages/ChatLayout.tsx
git commit -m "feat: extend ChatArea with drag-drop and ChatLayout with reply/edit/events"
```

---

### Task 18: Smoke Test Full Stack

- [ ] **Step 1: Start the full stack**

Run: `docker-compose up --build`
Expected: All services start. No errors from Liquibase migrations 013, 014.

- [ ] **Step 2: Manual smoke test**

Open `http://localhost:5173` in two browser windows:

1. Sign up two users, create a room, have both join
2. **File upload:** Click paperclip, select a file, send — verify file appears as attachment in the message
3. **Image upload:** Upload an image — verify thumbnail appears, click to open full-size viewer
4. **Reply:** Hover a message, click reply icon — verify reply bar appears, send reply — verify quoted preview shows
5. **Edit:** Hover own message, click edit icon — verify input enters edit mode, edit text, send — verify "(edited)" indicator shows
6. **Delete:** Hover own message, click delete icon, confirm — verify "This message was deleted" shows
7. **Emoji:** Click emoji button, select emoji — verify emoji inserted in input
8. **Drag-drop:** Drag a file over the chat area — verify drop overlay appears, drop file — verify it attaches

- [ ] **Step 3: Run existing backend tests**

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
