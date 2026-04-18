# Implementation Iterations

## Iteration 1: Complete Auth & User Management

**Goal:** Users can register with a unique username, manage their password, view/terminate sessions, and delete their account.

### Scope

- Add `username` field to registration (unique, immutable after creation)
- Password change (requires current password)
- Account deletion with password confirmation
- Active sessions list with selective logout
- Fix: signup should create a session automatically
- Remove `maximumSessions(1)` limit (needed for multi-tab later)

### Backend

**Entities:**
- `User`: add `username` (VARCHAR(32), UNIQUE, NOT NULL, immutable), `deleted_at` (nullable timestamp for soft-delete)

**DTOs:**
- `SignUpRequest`: add `username` with validation (`@NotBlank`, `@Size(min=3, max=32)`, alphanumeric + underscore)
- `UserResponse`: add `username`
- New `ChangePasswordRequest`: `currentPassword`, `newPassword`
- New `SessionResponse`: `sessionId`, `createdAt`, `lastAccessedAt`, `current` (boolean)

**Endpoints:**
- `PUT /api/users/me/password` — change password
- `DELETE /api/users/me` — delete account (requires password confirmation, invalidates all sessions)
- `GET /api/users/me/sessions` — list active sessions
- `POST /api/users/me/sessions/{sessionId}/invalidate` — terminate a specific session
- Fix `POST /api/users/signup` — create session on signup

**Dependencies:**
- Replace `spring-session-core` with `spring-session-jdbc` for persistent session storage

**Security:**
- Remove `maximumSessions(1)` from `SecurityConfig`
- `SessionAuthenticationFilter`: reject soft-deleted users
- Add `@Column(updatable = false)` on `username`

### Frontend

**Pages:**
- `SignUp.tsx`: add username field
- New `Settings.tsx`: tabbed layout with Profile (edit display name, read-only username/email), Security (change password, delete account with confirmation modal), Sessions (list with terminate button, highlight current)

**Components:**
- New `AppHeader.tsx`: top navigation bar with user dropdown (Settings, Logout)
- Update `Home.tsx` → integrate into new layout with header

**API layer:**
- `types.ts`: add `username` to `User`, new request/response interfaces
- New `api/users.ts`: `changePassword`, `deleteAccount`, `getSessions`, `terminateSession`

**Routes:**
- Add `/settings` (protected)

### Database Migrations

- `003-add-username-to-users.sql`: add `username` column, populate demo users ('alice', 'bob', 'carol'), set NOT NULL; add `deleted_at` column
- `004-spring-session-tables.sql`: create `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES` tables

### Key Decisions

- **spring-session-jdbc** gives session persistence across restarts and queryable session table — required for session management feature
- **Soft-delete** for users: `deleted_at` timestamp so messages from deleted users can show "deleted user" instead of null
- **Password reset** (email-based) is deferred — no email service in scope; only password change implemented

---

## Iteration 2: Chat Rooms & Real-time Messaging

**Goal:** Users can create public chat rooms, browse and join them, and send/receive messages in real-time with scrollable history.

### Scope

- WebSocket infrastructure (STOMP over SockJS)
- Public chat room CRUD (create, browse, join, leave)
- Real-time message sending and receiving
- Persistent message history with cursor-based infinite scroll
- Full chat UI layout (sidebar + chat area + message input)

### Backend

**Dependencies:**
- Add `spring-boot-starter-websocket`

**Entities:**
- `ChatRoom`: `id` (UUID), `name` (UNIQUE), `description`, `type` (PUBLIC), `owner_id` (FK), timestamps
- `ChatRoomMember`: `id`, `room_id` (FK), `user_id` (FK), `role` (OWNER/MEMBER), `joined_at`; unique on (room_id, user_id)
- `Message`: `id` (UUID), `room_id` (FK), `sender_id` (FK), `content` (TEXT), `created_at`, `updated_at`

**Configuration:**
- `WebSocketConfig`: STOMP endpoint at `/ws` with SockJS, simple broker for `/topic` and `/queue`, app prefix `/app`
- `WebSocketAuthInterceptor`: authenticate via HTTP session cookie on handshake

**Endpoints (REST):**
- `POST /api/rooms` — create room (creator becomes OWNER)
- `GET /api/rooms` — list rooms user is a member of
- `GET /api/rooms/public` — browse public rooms
- `GET /api/rooms/{roomId}` — room details
- `POST /api/rooms/{roomId}/join` — join public room
- `POST /api/rooms/{roomId}/leave` — leave room (owner cannot leave)
- `GET /api/rooms/{roomId}/messages?before={timestamp}&limit=50` — paginated history
- `GET /api/rooms/{roomId}/members` — list members

**WebSocket message flows:**
- Client sends → `/app/rooms/{roomId}/messages` — send message
- Server broadcasts → `/topic/rooms/{roomId}/messages` — new message to subscribers

**Services:**
- `ChatRoomService`: CRUD, join/leave, membership validation
- `MessageService`: save, paginate history
- `WebSocketMessageController`: `@MessageMapping` handler

### Frontend

**Dependencies:**
- Add `@stomp/stompjs`, `sockjs-client`

**Layout:**
- Replace `Home.tsx` with `ChatLayout.tsx`: left sidebar (room list) + main chat area + message input

**Components:**
- `ChatLayout.tsx`: main authenticated shell
- `RoomList.tsx`: sidebar with joined rooms, "Browse Rooms" button
- `RoomBrowser.tsx`: modal to browse/search public rooms and join
- `CreateRoomModal.tsx`: form (name, description)
- `ChatArea.tsx`: message list with infinite scroll
- `MessageInput.tsx`: text input with send
- `MessageBubble.tsx`: sender, timestamp, content
- `RoomHeader.tsx`: room name, member count, leave button

**Contexts/Hooks:**
- `useWebSocket.ts`: STOMP connection lifecycle, subscriptions, reconnection
- Chat state in `ChatLayout`: selected room, messages map

**API layer:**
- New `api/rooms.ts`: all REST calls
- `types.ts`: `ChatRoom`, `ChatRoomMember`, `Message`, `CreateRoomRequest`

### Database Migrations

- `005-create-chat-rooms.sql`: `chat_rooms` table
- `006-create-chat-room-members.sql`: `chat_room_members` table with unique (room_id, user_id)
- `007-create-messages.sql`: `messages` table with index on (room_id, created_at DESC)

### Key Decisions

- **Simple in-memory broker** (not RabbitMQ) — sufficient for single-server, up to 300 users
- **Cursor-based pagination** (`before` timestamp) — better than offset for chat history
- **WebSocket auth via HTTP session handshake** — `HttpSessionHandshakeInterceptor` copies session to WebSocket attributes
- **Server-side timestamps** for message ordering — no client clock dependency

---

## Iteration 3: Presence, Status & Notifications

**Goal:** Users see online/AFK/offline status for other users, and unread message counts per room.

### Scope

- Online/AFK/offline presence tracking
- Multi-tab support (online if any tab active, AFK if all idle >1 min, offline if no tabs)
- Real-time presence broadcasts via WebSocket
- Unread message indicators per room
- Mark-as-read when opening a room

### Backend

**Entities/Storage:**
- `UserPresence`: in-memory (`ConcurrentHashMap<Long, PresenceInfo>`) — not persisted to DB. `PresenceInfo` holds `status`, `activeConnections` count, `lastHeartbeatAt`
- `ReadReceipt`: `user_id` (FK), `room_id` (FK), `last_read_message_id` (FK), `last_read_at`; unique on (user_id, room_id)

**Endpoints:**
- `GET /api/rooms/{roomId}/members/presence` — batch presence for room members
- `POST /api/rooms/{roomId}/read` — mark room as read (body: `{lastReadMessageId}`)
- `GET /api/rooms/unread` — unread counts for all user's rooms

**WebSocket additions:**
- Client sends → `/app/presence/heartbeat` — periodic heartbeat (every 15s)
- Client sends → `/app/presence/status` — explicit AFK/active signal (on `visibilitychange`)
- Server broadcasts → `/topic/presence` — presence changes

**Services:**
- `PresenceService`: track connections, heartbeats, compute status, broadcast changes
- `UnreadService`: calculate unread counts via `ReadReceipt` vs message timestamps
- Extend `WebSocketEventListener` (`SessionConnectEvent` / `SessionDisconnectEvent`): increment/decrement connection count, trigger presence updates

**Presence logic:**
- CONNECT: increment connections, set ONLINE, broadcast
- DISCONNECT: decrement connections; if 0 → schedule OFFLINE after 30s timeout
- Heartbeat received: update `lastHeartbeatAt`, set ONLINE if was AFK
- No heartbeat for >60s on any connection: set AFK
- Tab hidden (`visibilitychange`): client sends AFK signal; server sets AFK only if all connections report AFK

### Frontend

**Components:**
- `PresenceIndicator.tsx`: green (online) / yellow (AFK) / gray (offline) dot
- `UnreadBadge.tsx`: numeric badge on room list items
- Extend `RoomList.tsx`: add unread badges, sort by recent activity
- Extend member lists: add presence dots next to usernames

**Hooks:**
- `usePresence.ts`: subscribe to `/topic/presence`, maintain presence map
- `useUnread.ts`: fetch unread counts on load, update on new messages and mark-as-read
- Extend `useWebSocket.ts`: send heartbeats every 15s, listen to `visibilitychange` for AFK signals

**Multi-tab handling:**
- Each tab connects independently via WebSocket
- Server counts connections per user (source of truth)
- No `BroadcastChannel` coordination needed — server handles it

### Database Migrations

- `008-create-read-receipts.sql`: `read_receipts` table with unique (user_id, room_id)

### Key Decisions

- **In-memory presence** (not DB) — fast, acceptable for single-server; lost on restart is fine since presence is ephemeral
- **Each tab = independent WebSocket** — server counts connections; simpler than client-side tab coordination
- **Heartbeat interval: 15s**, AFK timeout: 60s no heartbeat, offline timeout: 30s after last disconnect
- **Unread counts via direct query** — `COUNT(*) FROM messages WHERE room_id = ? AND created_at > last_read_at` — fine for moderate scale

---

## Iteration 4: Private Rooms, Contacts & Personal Messaging

**Goal:** Users can create private (invite-only) rooms, manage a friends list, block users, and have direct message conversations.

### Scope

- Private rooms (not in public catalog, join by invitation only)
- Room invitations (send/accept/decline)
- Friend requests (send/accept/decline/remove)
- User-to-user bans (block contact, freeze DM history)
- Direct messages as two-person DIRECT-type chat rooms
- User search by username

### Backend

**Entities:**
- `ChatRoom.type`: add `PRIVATE` and `DIRECT` enum values
- `RoomInvitation`: `id`, `room_id`, `inviter_id`, `invitee_id`, `status` (PENDING/ACCEPTED/DECLINED), timestamps
- `Friendship`: `id`, `requester_id`, `addressee_id`, `status` (PENDING/ACCEPTED/DECLINED), timestamps; unique on (requester_id, addressee_id)
- `UserBlock`: `id`, `blocker_id`, `blocked_id`, `created_at`; unique on (blocker_id, blocked_id)

**Endpoints:**
- `POST /api/rooms` — extend to support `type: PRIVATE`
- `POST /api/rooms/{roomId}/invitations` — invite user to private room
- `GET /api/users/me/invitations` — pending room invitations
- `POST /api/invitations/{id}/accept` — accept (auto-joins room)
- `POST /api/invitations/{id}/decline`
- `POST /api/friends/request` — send friend request
- `GET /api/friends` — list accepted friends
- `GET /api/friends/requests` — pending requests (incoming + outgoing)
- `POST /api/friends/{id}/accept`
- `POST /api/friends/{id}/decline`
- `DELETE /api/friends/{id}` — remove friend
- `POST /api/users/{userId}/block` — block user
- `DELETE /api/users/{userId}/block` — unblock
- `GET /api/users/me/blocks` — list blocked users
- `POST /api/direct-messages/{userId}` — get or create DM room
- `GET /api/users/search?q=...` — search users by username/display name

**WebSocket additions:**
- `/queue/notifications` — friend requests, room invitations delivered in real-time

**Services:**
- `FriendshipService`, `BlockService`, `RoomInvitationService`
- Extend `ChatRoomService`: private room visibility, DIRECT room find-or-create
- Extend `MessageService`: enforce block checks on DMs

### Frontend

**Components:**
- Extend `RoomList.tsx`: separate sections for Rooms and Direct Messages
- `ContactsPanel.tsx`: friends list, pending requests, blocked users
- `UserSearchModal.tsx`: search to add friends or invite to rooms
- `FriendRequestList.tsx`: incoming/outgoing with accept/decline
- `InvitationList.tsx`: pending room invitations
- `InviteToRoomModal.tsx`: invite friend to private room
- `UserProfilePopover.tsx`: click username → profile with add friend / block / send DM actions

**API layer:**
- New `api/friends.ts`, `api/blocks.ts`, `api/invitations.ts`, `api/directMessages.ts`

### Database Migrations

- `009-create-room-invitations.sql`
- `010-create-friendships.sql`
- `011-create-user-blocks.sql`

### Key Decisions

- **DMs as DIRECT room type** — reuses the same messages table and WebSocket infra; prevents duplicate messaging systems
- **Single friendship row** (not symmetric pair) — query with `WHERE requester_id = ? OR addressee_id = ?`
- **Block enforcement:** server-side in DMs (reject message send), client-side in group rooms (hide messages from blocked users)
- **Personal messaging requires friendship** — enforced at service layer

---

## Iteration 5: Attachments & Rich Messaging

**Goal:** Users can upload files/images, edit and delete messages, reply with quoted previews, and use emoji.

### Scope

- File/image upload via button, drag-drop, and paste
- Local filesystem storage with access control
- Message editing with "edited" indicator
- Message deletion (soft-delete)
- Message replies with quoted preview
- Emoji picker

### Backend

**Configuration:**
- `application.yml`: `spring.servlet.multipart.max-file-size=20MB` (files), image validation at 3MB in service layer
- Upload directory configurable via env var, default `./uploads`
- Docker: volume mount for uploads

**Entities:**
- `Attachment`: `id` (UUID), `message_id` (FK), `file_name`, `original_file_name`, `content_type`, `file_size`, `storage_path`, `created_at`
- `Message`: add `reply_to_id` (FK, self-referencing, nullable), `edited_at` (nullable), `deleted_at` (nullable)

**Endpoints:**
- `POST /api/rooms/{roomId}/messages` — extend to accept multipart (text + files)
- `GET /api/attachments/{attachmentId}` — download file (verify room membership)
- `PUT /api/rooms/{roomId}/messages/{messageId}` — edit message text (sender only)
- `DELETE /api/rooms/{roomId}/messages/{messageId}` — soft-delete (sender or room owner)

**WebSocket additions:**
- Extend message events with types: `NEW_MESSAGE`, `MESSAGE_EDITED`, `MESSAGE_DELETED` on `/topic/rooms/{roomId}/events`

**Services:**
- `FileStorageService`: save to disk with UUID filename, validate size
- `AttachmentService`: create record, link to message
- Extend `MessageService`: `editMessage`, `deleteMessage`, `replyToId` handling

### Frontend

**Dependencies:**
- Add emoji picker library (e.g., `@emoji-mart/react`)

**Components:**
- Extend `MessageBubble.tsx`: "edited" indicator, reply preview (quoted message), attachment thumbnails/links, edit/delete buttons on hover
- Extend `MessageInput.tsx`: file upload button, drag-drop zone, paste handler, reply bar with cancel, emoji button
- `AttachmentPreview.tsx`: image thumbnail or file icon with name/size
- `ImageViewer.tsx`: modal for full-size images
- `EmojiPicker.tsx`: emoji selector integrated into message input

**API layer:**
- Extend `api/rooms.ts`: `uploadAttachment`, `editMessage`, `deleteMessage`
- New `api/attachments.ts`: download URLs

### Database Migrations

- `012-create-attachments.sql`
- `013-add-message-reply-and-edit.sql`: add `reply_to_id`, `edited_at`, `deleted_at` to messages

### Key Decisions

- **Files uploaded via REST** (multipart POST), not WebSocket — message with attachment ref then broadcast via WebSocket
- **Local filesystem storage** as specified; `storage_path` stores relative path
- **Access control per-request** on file download — verify room membership
- **Soft-delete messages** — show "This message was deleted" in UI, preserve timeline slot; replies to deleted messages show "[deleted]" as quoted content
- **Emoji picker lazy-loaded** to avoid bundle bloat (~1MB data)

---

## Iteration 6: Moderation & Administration

**Goal:** Room owners and admins can fully moderate rooms — manage members, admins, bans, room settings, and delete rooms. Public room catalog with search.

### Scope

- Admin role for chat rooms (OWNER can promote/demote)
- Ban/unban users from rooms
- Kick members (treated as ban)
- Admin message deletion in rooms
- Room settings editing (name, description, visibility)
- Room deletion with cascade (all messages, files, members deleted)
- Public room catalog with text search

### Backend

**Entities:**
- `ChatRoomMember.role`: add `ADMIN` value
- `RoomBan`: `id`, `room_id` (FK), `user_id` (FK), `banned_by_id` (FK), `reason`, `created_at`; unique on (room_id, user_id)

**Endpoints:**
- `PUT /api/rooms/{roomId}/members/{userId}/role` — promote to ADMIN or demote to MEMBER (OWNER only)
- `POST /api/rooms/{roomId}/bans` — ban user (OWNER/ADMIN); removes from room, prevents rejoin
- `DELETE /api/rooms/{roomId}/bans/{userId}` — unban
- `GET /api/rooms/{roomId}/bans` — list banned users with who-banned-whom
- `DELETE /api/rooms/{roomId}/members/{userId}` — kick/remove member (OWNER/ADMIN)
- `PUT /api/rooms/{roomId}` — edit room settings (OWNER/ADMIN)
- `DELETE /api/rooms/{roomId}` — delete room with cascade (OWNER only)
- `GET /api/rooms/public?search=...&page=...` — extend with text search and pagination
- Extend `DELETE /api/rooms/{roomId}/messages/{messageId}` — allow ADMIN/OWNER deletion

**WebSocket additions:**
- Notify banned/kicked user in real-time to remove room from their UI

**Services:**
- `ModerationService`: ban/unban, kick, role management with permission hierarchy (OWNER > ADMIN > MEMBER)
- Extend `ChatRoomService`: room settings update, room deletion with cascade
- Extend `MessageService`: allow admin/owner deletion

**Permission hierarchy:**
- OWNER can: everything + delete room + remove any admin
- ADMIN can: delete messages, kick members, ban members, unban, remove admin from other admins (not owner)
- ADMIN cannot: kick/ban owner, demote owner, delete room

### Frontend

**Components:**
- `ManageRoomModal.tsx`: tabbed modal (Members, Admins, Banned Users, Invitations, Settings) per wireframe
- `MembersTab.tsx`: member list with role, status, and action buttons (Make Admin, Ban, Remove)
- `AdminsTab.tsx`: admin list, remove admin button (owner cannot be removed)
- `BannedUsersTab.tsx`: banned users with banned-by info and unban button
- `RoomSettingsTab.tsx`: edit name, description, visibility; delete room button with confirmation
- `RoomCatalog.tsx`: full page for browsing public rooms with search and pagination
- Extend `RoomHeader.tsx`: "Manage Room" button (visible to OWNER/ADMIN)
- Extend `MessageBubble.tsx`: admin delete action

**API layer:**
- New `api/moderation.ts`: ban, unban, kick, role management
- Extend `api/rooms.ts`: update settings, delete room, catalog search

### Database Migrations

- `014-create-room-bans.sql`
- `015-add-room-search-index.sql`: text search index on room name (pg_trgm or simple ILIKE for moderate scale)

### Key Decisions

- **Room deletion cascade** via `ON DELETE CASCADE` on foreign keys — simpler than application-level cascade
- **Room ban = kick + block rejoin** — kicking a member always creates a ban record
- **Text search**: simple `ILIKE '%query%'` for moderate scale; pg_trgm index if needed
- **Permission checks in service layer** — every moderation action validates caller's role against target's role
