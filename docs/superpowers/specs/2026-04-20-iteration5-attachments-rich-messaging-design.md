# Iteration 5: Attachments & Rich Messaging — Design Spec

**Goal:** Users can upload files/images, edit and delete messages, reply with quoted previews, and use an emoji picker.

## Architecture

Three areas of change: (1) new `Attachment` entity with local filesystem storage and a REST upload/download API with membership-based access control, (2) extend `Message` entity with `reply_to_id`, `edited_at`, and `deleted_at` columns for replies, editing, and soft-delete, (3) new WebSocket event channel `/topic/rooms/{roomId}/events` for edit/delete broadcasts (new messages continue on existing `/topic/rooms/{roomId}/messages` topic). Frontend adds file upload UI (button, drag-drop, clipboard paste), reply bar, inline edit/delete actions on messages, and a lazy-loaded emoji picker.

## Backend

### Entities

**Attachment:**
- `id` — UUID, primary key
- `message_id` — UUID, FK to messages.id, NOT NULL
- `file_name` — VARCHAR, NOT NULL (UUID-based name on disk)
- `original_file_name` — VARCHAR, NOT NULL (user's original filename)
- `content_type` — VARCHAR, NOT NULL (MIME type)
- `file_size` — BIGINT, NOT NULL (bytes)
- `storage_path` — VARCHAR, NOT NULL (relative path under upload directory)
- `created_at` — TIMESTAMP, NOT NULL

**Message (extend):**
- `reply_to_id` — UUID, FK to messages.id, nullable, self-referencing
- `edited_at` — TIMESTAMP, nullable (set on first edit, updated on subsequent edits)
- `deleted_at` — TIMESTAMP, nullable (soft-delete marker)

### Configuration

- `application.yml`: `spring.servlet.multipart.max-file-size=20MB`, `spring.servlet.multipart.max-request-size=25MB`
- New config property `chatapp.upload-dir` (default `./uploads`), injected via `@Value`
- Docker Compose: volume mount `./uploads:/app/uploads` for persistence
- Upload directory created on application startup if it doesn't exist (via `@PostConstruct` in `FileStorageService`)

### REST Endpoints

**MessageController (new REST controller, separate from WebSocketMessageController):**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/rooms/{roomId}/messages` | Member | Send message with optional attachments (multipart: `content` text part + `files` file parts + optional `replyToId` text part) |
| PUT | `/api/rooms/{roomId}/messages/{messageId}` | Sender only | Edit message text (JSON body: `{content}`) |
| DELETE | `/api/rooms/{roomId}/messages/{messageId}` | Sender or room owner | Soft-delete message |

**AttachmentController (new):**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/attachments/{attachmentId}` | Room member | Download file (verify caller is member of the attachment's room) |
| GET | `/api/attachments/{attachmentId}/thumbnail` | Room member | Download image thumbnail (only for image content types, returns 404 for non-images) |

### Sending Messages with Attachments

Messages with attachments use REST instead of WebSocket because multipart file upload isn't practical over STOMP. The flow:

1. Client sends `POST /api/rooms/{roomId}/messages` as `multipart/form-data` with fields:
   - `content` (String, optional if files present — at least one of content or files required)
   - `replyToId` (String/UUID, optional)
   - `files` (MultipartFile[], optional, max 5 files per message)
2. Server validates membership, saves message to DB, stores files to disk, creates `Attachment` records
3. Server broadcasts the full `MessageResponse` (including attachment metadata and reply preview) to `/topic/rooms/{roomId}/messages` via `SimpMessagingTemplate`

Plain text messages without attachments can still be sent via the existing WebSocket `@MessageMapping("/rooms/{roomId}/messages")`. The WebSocket controller is extended to support `replyToId` in the payload.

### WebSocket

**Existing channel (unchanged purpose):**
- `/topic/rooms/{roomId}/messages` — new message broadcasts (both from WebSocket sends and REST sends with attachments)

**New channel:**
- `/topic/rooms/{roomId}/events` — message lifecycle events, payload: `{type, data}`
  - `MESSAGE_EDITED`: `{type: "MESSAGE_EDITED", data: MessageResponse}` — full updated message
  - `MESSAGE_DELETED`: `{type: "MESSAGE_DELETED", data: {messageId: UUID}}` — just the ID

**Extend WebSocketMessageController:**
- `SendMessageRequest` gains optional `replyToId` (UUID) field
- After saving, if `replyToId` is set, load the replied-to message and include its preview in the broadcast

### Services

**FileStorageService (new):**
- `store(MultipartFile file)` — validates file size (images ≤ 3MB by content type, other files ≤ 20MB), generates UUID filename preserving extension, saves to `{uploadDir}/{yyyy}/{MM}/{uuid}.{ext}`, returns relative `storagePath`
- `load(String storagePath)` — returns `Resource` for file download
- `delete(String storagePath)` — deletes file from disk (used if message save fails after file write)
- Image validation: check `contentType.startsWith("image/")` for the 3MB limit
- Directory structure: `{uploadDir}/2026/04/` — organized by year/month to avoid single-directory bloat

**AttachmentService (new):**
- `createAttachments(Message message, List<MultipartFile> files)` — iterates files, calls `FileStorageService.store()`, creates `Attachment` entity for each, returns list
- `getAttachment(UUID attachmentId)` — loads attachment record, used by controller for download
- `getAttachmentsByMessageId(UUID messageId)` — returns attachments for a message

**MessageService (extend):**
- `sendMessage(ChatRoom room, User sender, String content, UUID replyToId)` — overloaded to accept `replyToId`. If provided, validates the replied-to message exists and belongs to the same room. Sets `message.replyTo`.
- `editMessage(UUID messageId, Long userId, String newContent)` — validates sender matches, message is not deleted, content is not blank. Updates `content` and sets `editedAt = Instant.now()`. Returns updated message.
- `deleteMessage(UUID messageId, Long userId, UUID roomId)` — validates caller is sender OR room owner. Sets `deletedAt = Instant.now()`. Does NOT delete attachment files (they remain on disk but are inaccessible since the message is soft-deleted).
- `findById(UUID messageId)` — existing or new method to load a single message

### DTOs

**Requests:**
- `SendMessageRequest` (extend): add `replyToId` (UUID, optional)
- `EditMessageRequest` (new): `content` (String, @NotBlank, @Size(max=3000))

**Responses:**
- `MessageResponse` (extend): add fields:
  - `replyToId` (UUID, nullable)
  - `replyToPreview` (ReplyPreview, nullable) — preview of the replied-to message
  - `editedAt` (Instant, nullable)
  - `deleted` (boolean) — true if `deletedAt` is set
  - `attachments` (List<AttachmentResponse>) — empty list if none
  - When `deleted` is true: `content` is replaced with null, `attachments` is empty, `replyToPreview` is null
- `ReplyPreview` (new): `messageId` (UUID), `senderId` (Long), `senderUsername` (String), `senderDisplayName` (String), `content` (String, truncated to 100 chars), `deleted` (boolean)
- `AttachmentResponse` (new): `id` (UUID), `originalFileName` (String), `contentType` (String), `fileSize` (Long), `downloadUrl` (String — `/api/attachments/{id}`), `thumbnailUrl` (String, nullable — `/api/attachments/{id}/thumbnail`, only for images)
- `MessageEventResponse` (new): `type` (String — "MESSAGE_EDITED" or "MESSAGE_DELETED"), `data` (Object — MessageResponse or {messageId})

### Error Handling

- Upload too large: Spring's `MaxUploadSizeExceededException` → 413 response
- Image over 3MB: 400 with message "Image files must not exceed 3MB"
- File count over 5: 400 with message "Maximum 5 files per message"
- Edit non-own message: 403
- Edit deleted message: 400
- Delete by non-sender non-owner: 403

## Frontend

### Dependencies

- Add `@emoji-mart/react` and `@emoji-mart/data` — emoji picker component and data

### Components

**MessageBubble.tsx (extend):**
- Add "edited" indicator: gray "(edited)" text next to timestamp when `editedAt` is set
- Add reply preview: if `replyToPreview` exists, show a quoted block above the message content with sender name and truncated text (clicking it scrolls to the original message if visible). If `replyToPreview.deleted` is true, show "[deleted message]"
- Add attachment rendering: for each attachment, show `AttachmentPreview` inline. Images show thumbnails, other files show icon + filename + size
- Add action buttons on hover (visible on own messages): Edit (pencil icon), Delete (trash icon). Delete also visible on other's messages if current user is room owner
- When `deleted` is true: show "This message was deleted" in italic gray text, no content/attachments/actions

**MessageInput.tsx (extend):**
- Add file upload button (paperclip icon) left of the text area — opens file picker
- Add drag-drop zone: entire chat area becomes a drop target, visual overlay on dragover
- Add clipboard paste handler: if paste event contains files (e.g., screenshot), attach them
- Add file preview row: show selected files above the input as chips (filename + remove button) before sending
- Add reply bar: when replying, show a bar above the input with "Replying to {username}: {truncated content}" and an X button to cancel
- Add emoji button (smiley icon) — opens emoji picker popover
- Enforce max 5 files — disable add button after 5, show message if user tries to add more
- Send via REST (`POST /api/rooms/{roomId}/messages` multipart) when files are attached, via WebSocket when text-only (for lower latency)

**AttachmentPreview.tsx (new):**
- For images (`contentType.startsWith("image/")`: show thumbnail (via `/api/attachments/{id}/thumbnail`) that opens `ImageViewer` on click
- For other files: show file icon (based on extension/content type), original filename (truncated), and human-readable file size. Click triggers download via `/api/attachments/{id}`

**ImageViewer.tsx (new):**
- Modal overlay showing full-size image (via `/api/attachments/{id}`)
- Close on click outside, Escape key, or X button
- Download button in modal toolbar

**EmojiPicker.tsx (new):**
- Wrapper around `@emoji-mart/react` Picker component
- Rendered in an Ant Design Popover, triggered by emoji button in MessageInput
- On select: insert emoji character at cursor position in the text area
- Lazy-loaded via `React.lazy()` to avoid adding ~1MB to initial bundle

**ChatArea.tsx (extend):**
- Add drag-drop event handlers: `onDragOver`, `onDragLeave`, `onDrop` — forward dropped files to MessageInput
- Show drop overlay ("Drop files here") when dragging files over the area
- Handle `MESSAGE_EDITED` events: update the message in the messages array by ID
- Handle `MESSAGE_DELETED` events: mark the message as deleted in the messages array (set `deleted: true`, clear `content`)
- Add `onReply(message)` callback: when user clicks reply on a MessageBubble, set reply state in parent
- Add `onEdit(message)` callback: when user clicks edit, populate MessageInput with message content in edit mode
- Add scroll-to-message behavior: when clicking a reply preview, scroll to the referenced message and briefly highlight it

### State Management

Chat state in `ChatLayout` gains:
- `replyTo: ChatMessage | null` — the message being replied to (shown in reply bar)
- `editingMessage: ChatMessage | null` — the message being edited (MessageInput switches to edit mode)
- Event subscription: subscribe to `/topic/rooms/{roomId}/events` alongside existing `/messages` subscription
- On `MESSAGE_EDITED` event: update the message in the `messages` map
- On `MESSAGE_DELETED` event: mark the message as deleted in the `messages` map

**useWebSocket.ts (extend):**
- Add subscription to `/topic/rooms/{roomId}/events` when subscribing to a room
- New callback: `onEvent: (roomId: string, event: MessageEvent) => void` in options
- Unsubscribe from events topic when unsubscribing from a room

### API Layer

**Extend `api/rooms.ts`:**
- `sendMessageWithAttachments(roomId, content, files, replyToId)` — `POST /api/rooms/{roomId}/messages` as `multipart/form-data`
- `editMessage(roomId, messageId, content)` — `PUT /api/rooms/{roomId}/messages/{messageId}`
- `deleteMessage(roomId, messageId)` — `DELETE /api/rooms/{roomId}/messages/{messageId}`

**New `api/attachments.ts`:**
- `getDownloadUrl(attachmentId)` — returns `/api/attachments/{attachmentId}` (simple URL builder)
- `getThumbnailUrl(attachmentId)` — returns `/api/attachments/{attachmentId}/thumbnail`

**Extend `api/types.ts`:**
- `AttachmentInfo`: `id`, `originalFileName`, `contentType`, `fileSize`, `downloadUrl`, `thumbnailUrl`
- `ReplyPreview`: `messageId`, `senderId`, `senderUsername`, `senderDisplayName`, `content`, `deleted`
- `ChatMessage` (extend): add `replyToId`, `replyToPreview`, `editedAt`, `deleted`, `attachments`
- `MessageEvent`: `type` ("MESSAGE_EDITED" | "MESSAGE_DELETED"), `data` (ChatMessage | {messageId: string})
- `EditMessageRequest`: `content`

## Database Migrations

- `013-create-attachments.sql` — `attachments` table: id (UUID PK), message_id (UUID FK to messages.id ON DELETE CASCADE), file_name (VARCHAR NOT NULL), original_file_name (VARCHAR NOT NULL), content_type (VARCHAR NOT NULL), file_size (BIGINT NOT NULL), storage_path (VARCHAR NOT NULL), created_at (TIMESTAMP NOT NULL DEFAULT NOW()). Index on message_id.
- `014-add-message-reply-and-edit.sql` — alter messages table: add `reply_to_id` (UUID, FK to messages.id ON DELETE SET NULL, nullable), add `edited_at` (TIMESTAMP, nullable), add `deleted_at` (TIMESTAMP, nullable). Index on reply_to_id.

## Key Decisions

1. **Files uploaded via REST** (multipart POST), not WebSocket — STOMP doesn't handle binary uploads well; after REST save, the server broadcasts the message (with attachment metadata) via WebSocket to all subscribers
2. **Local filesystem storage** as specified in requirements — files stored under `{uploadDir}/{yyyy}/{MM}/{uuid}.{ext}`; `storage_path` column stores the relative path
3. **Access control per-request** on file download — `AttachmentController` loads the attachment, resolves its room, verifies caller is a member. No pre-signed URLs or tokens.
4. **Soft-delete messages** — `deleted_at` timestamp; UI shows "This message was deleted" preserving timeline position. Attachments of deleted messages are not served (controller checks `message.deletedAt`). Replies to deleted messages show "[deleted message]" as quoted content.
5. **Reply via self-referencing FK** — `reply_to_id` points to another message in the same room; `ON DELETE SET NULL` so deleting the original doesn't break the reply chain. Reply preview is eagerly loaded when building `MessageResponse`.
6. **Dual send path** — text-only messages continue via WebSocket `@MessageMapping` for low latency; messages with attachments use REST. Both paths broadcast to the same `/topic/rooms/{roomId}/messages` topic.
7. **Emoji picker lazy-loaded** — `@emoji-mart/data` is ~1MB; `React.lazy()` ensures it's only loaded when the user clicks the emoji button
8. **Image size limit enforced in service layer** — Spring's multipart config allows 20MB; `FileStorageService` rejects images over 3MB with a 400 error, keeping the distinction between file and image size limits
9. **Thumbnail endpoint** — separate `/thumbnail` path for images; serves the full image with `Content-Disposition: inline` (no server-side resizing). Frontend CSS constrains display to thumbnail size (max 200x200px). A future iteration could add server-side resize.
10. **Events channel separate from messages** — `/topic/rooms/{roomId}/events` carries edit/delete events to avoid changing the existing message subscription contract; clients that don't support editing yet continue to work unchanged

## Testing Strategy

Two separate plans will be created:

**Implementation Plan:** Backend: migrations, Attachment entity/repository, FileStorageService, AttachmentService, extend Message entity, extend MessageService (edit/delete/reply), MessageController (REST), AttachmentController, extend WebSocketMessageController (replyToId), extend MessageResponse/DTOs. Frontend: extend types, extend useWebSocket (events subscription), extend MessageBubble (edit/delete/reply/attachments), extend MessageInput (file upload/reply bar/emoji), AttachmentPreview, ImageViewer, EmojiPicker, extend ChatArea (drag-drop/events), extend ChatLayout state, API functions.

**Testing Plan:** Backend integration tests (JUnit 5 + Testcontainers) for file upload/download, message edit/delete, reply creation, attachment access control. E2E tests (Playwright) for file upload via button, message editing flow, message deletion, reply with quote preview, emoji insertion.
