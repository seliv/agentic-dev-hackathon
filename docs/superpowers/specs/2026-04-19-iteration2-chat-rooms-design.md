# Iteration 2: Chat Rooms & Real-time Messaging — Design Spec

**Goal:** Users can create public chat rooms, browse and join them, and send/receive messages in real-time with scrollable history.

## Architecture

Three new entities (`ChatRoom`, `ChatRoomMember`, `Message`) following existing Spring Boot layered architecture. WebSocket via STOMP with simple in-memory broker for real-time messaging (no SockJS — plain native WebSocket). REST endpoints for room CRUD, membership, and message history. Frontend replaces `Home.tsx` with a chat layout: right sidebar (rooms list per requirements 4.1.1) + center chat area + message input.

## Backend

### Entities

**ChatRoom:**
- `id` — UUID, primary key
- `name` — VARCHAR, UNIQUE, NOT NULL
- `description` — TEXT, nullable
- `type` — VARCHAR (enum: PUBLIC), default PUBLIC
- `owner_id` — BIGINT, FK to users.id, NOT NULL
- `created_at`, `updated_at` — TIMESTAMP

**ChatRoomMember:**
- `id` — BIGSERIAL, primary key
- `room_id` — UUID, FK to chat_rooms.id, NOT NULL
- `user_id` — BIGINT, FK to users.id, NOT NULL
- `role` — VARCHAR (enum: OWNER, MEMBER), NOT NULL
- `joined_at` — TIMESTAMP, NOT NULL
- UNIQUE constraint on (room_id, user_id)

**Message:**
- `id` — UUID, primary key
- `room_id` — UUID, FK to chat_rooms.id, NOT NULL
- `sender_id` — BIGINT, FK to users.id, NOT NULL
- `content` — TEXT, NOT NULL (max 3KB enforced at validation layer)
- `created_at`, `updated_at` — TIMESTAMP
- INDEX on (room_id, created_at DESC) for efficient history queries

### REST Endpoints (ChatRoomController)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/rooms` | Required | Create room (creator becomes OWNER member) |
| GET | `/api/rooms` | Required | List rooms user is a member of |
| GET | `/api/rooms/public?search=&page=&size=` | Required | Browse/search public rooms (ILIKE on name) |
| GET | `/api/rooms/{roomId}` | Member | Room details |
| POST | `/api/rooms/{roomId}/join` | Required | Join public room |
| POST | `/api/rooms/{roomId}/leave` | Member | Leave room (owner cannot leave) |
| GET | `/api/rooms/{roomId}/members` | Member | List room members |
| GET | `/api/rooms/{roomId}/messages?before={ISO timestamp}&limit=50` | Member | Cursor-based message history |

### WebSocket

- STOMP endpoint at `/ws` (native WebSocket, no SockJS)
- Auth via `HttpSessionHandshakeInterceptor` — copies HTTP session to WebSocket attributes
- `ChannelInterceptor` on inbound channel — extracts user from session, validates membership
- Simple in-memory broker for `/topic` and `/queue`, app destination prefix `/app`

**Message flows:**
- Client sends → `/app/rooms/{roomId}/messages` with JSON payload `{content}`
- Server validates membership, saves to DB with server-side timestamp
- Server broadcasts to `/topic/rooms/{roomId}/messages` with full `MessageResponse`

### Services

- `ChatRoomService` — create room, join/leave, membership validation, list rooms, search public rooms
- `MessageService` — save message, cursor-based pagination (before timestamp + limit)

### DTOs

**Requests:** `CreateRoomRequest` (name, description), `SendMessageRequest` (content)
**Responses:** `ChatRoomResponse` (id, name, description, type, owner, memberCount, createdAt), `ChatRoomMemberResponse` (userId, username, displayName, role, joinedAt), `MessageResponse` (id, roomId, senderId, senderUsername, senderDisplayName, content, createdAt, updatedAt)

## Frontend

### Layout

`ChatLayout.tsx` replaces `Home.tsx` as the main authenticated view. `AppHeader` on top, content area in center, room list sidebar on the right (per requirements 4.1.1 and wireframe).

### Components

- `ChatLayout.tsx` — authenticated shell: header + main content + right sidebar
- `RoomList.tsx` — sidebar listing joined rooms, "Browse Rooms" and "Create Room" buttons
- `RoomBrowser.tsx` — modal to browse/search public rooms with join button, ILIKE search field
- `CreateRoomModal.tsx` — form with name + description fields
- `ChatArea.tsx` — message list with infinite scroll (cursor-based, loads older on scroll up), empty state when no room selected
- `MessageInput.tsx` — text input, send on Enter (Shift+Enter for newline), send button
- `MessageBubble.tsx` — sender name, timestamp, content; own messages styled differently
- `RoomHeader.tsx` — room name, member count, leave button (hidden for owner)

### State Management

- `useWebSocket.ts` hook — STOMP client lifecycle (connect/disconnect/reconnect), subscription management per room, send message helper
- Chat state in `ChatLayout` — selected room ID, messages map (roomId → Message[]), room list

### API Layer

- New `api/rooms.ts` — createRoom, getMyRooms, getPublicRooms, getRoomDetails, joinRoom, leaveRoom, getMembers, getMessages
- Extend `api/types.ts` — ChatRoom, ChatRoomMember, Message, CreateRoomRequest

### Dependencies

- Add `@stomp/stompjs` (STOMP client, no SockJS needed)

## Database Migrations

- `005-create-chat-rooms.sql` — chat_rooms table with unique name constraint
- `006-create-chat-room-members.sql` — chat_room_members table with unique (room_id, user_id)
- `007-create-messages.sql` — messages table with index on (room_id, created_at DESC)

## Key Decisions

1. **Simple in-memory broker** (not RabbitMQ) — sufficient for single-server, up to 300 users
2. **Cursor-based pagination** (`before` timestamp + limit) — better than offset for chat history where new messages shift offsets
3. **WebSocket auth via HTTP session handshake** — `HttpSessionHandshakeInterceptor` copies session to WebSocket attributes; `ChannelInterceptor` validates on each message
4. **Server-side timestamps** for message ordering — no client clock dependency
5. **No SockJS** — all modern browsers support native WebSocket; reduces complexity
6. **Right sidebar** — matches requirements 4.1.1 and wireframe
7. **Room search from the start** — ILIKE on room name, avoids rebuilding the catalog later
8. **UUID for rooms and messages** — natural for distributed IDs, no sequence conflicts
9. **Room name uniqueness** — enforced at DB level (unique constraint) and validated in service

## Testing Strategy

Two separate plans will be created:

**Implementation Plan:** Backend entities, services, controllers, WebSocket config, migrations, then frontend components, hooks, pages.

**Testing Plan:** Backend integration tests (JUnit 5 + Testcontainers) for all REST endpoints and WebSocket messaging. E2E tests (Playwright) for room creation, browsing, joining, messaging, and leaving.
