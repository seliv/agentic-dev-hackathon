# Iteration 4: Presence, Status & Notifications — Design Spec

**Goal:** Users see online/AFK/offline status for other users, and unread message counts per room with mark-as-read behavior.

## Architecture

In-memory presence tracking via `ConcurrentHashMap` (not persisted — presence is ephemeral). New `ReadReceipt` entity for unread count computation. Presence changes broadcast via `/topic/presence`. Client sends periodic heartbeats and visibility-change signals. Unread counts calculated by comparing `ReadReceipt.last_read_at` against message timestamps. Frontend extends the existing `useWebSocket` hook with heartbeat/presence logic and adds unread badge + presence indicator components.

## Backend

### Entities / Storage

**UserPresence (in-memory only):**
- Stored in `ConcurrentHashMap<Long, PresenceInfo>` inside `PresenceService`
- `PresenceInfo` fields:
  - `status` — enum: `ONLINE`, `AFK`, `OFFLINE`
  - `activeConnections` — `int`, count of open WebSocket sessions for this user
  - `afkConnections` — `int`, count of connections reporting AFK
  - `lastHeartbeatAt` — `Instant`, updated on each heartbeat

**ReadReceipt (persisted):**
- `id` — BIGSERIAL, primary key
- `user_id` — BIGINT, FK to users.id, NOT NULL
- `room_id` — UUID, FK to chat_rooms.id, NOT NULL
- `last_read_message_id` — UUID, FK to messages.id, NOT NULL
- `last_read_at` — TIMESTAMP, NOT NULL
- UNIQUE constraint on (user_id, room_id)

### REST Endpoints

**PresenceController:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/rooms/{roomId}/members/presence` | Member | Batch presence for all room members |

**UnreadController:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/rooms/{roomId}/read` | Member | Mark room as read (body: `{lastReadMessageId}`) |
| GET | `/api/rooms/unread` | Required | Unread counts for all rooms user is a member of |

### WebSocket

**New message mappings (new WebSocketPresenceController):**

- Client sends -> `/app/presence/heartbeat` — periodic heartbeat (every 15s), no payload needed
- Client sends -> `/app/presence/status` — explicit AFK/active signal, payload: `{status: "AFK" | "ACTIVE"}`
- Server broadcasts -> `/topic/presence` — presence change events, payload: `{userId, username, status}`

**Event listeners (new WebSocketEventListener):**

- `@EventListener(SessionConnectEvent)` — extract `user_id` from session attributes, increment `activeConnections`, set ONLINE, broadcast presence change
- `@EventListener(SessionDisconnectEvent)` — decrement `activeConnections`; if 0, schedule OFFLINE after 30s timeout (via `ScheduledExecutorService`); broadcast if status changes

### Presence Logic

```
CONNECT:
  activeConnections++
  status = ONLINE
  broadcast change

DISCONNECT:
  activeConnections--
  if activeConnections == 0:
    schedule OFFLINE after 30s
    (cancelled if reconnect happens within 30s)
  broadcast if status changed

HEARTBEAT received:
  update lastHeartbeatAt
  if was AFK and not all connections AFK:
    status = ONLINE
    broadcast change

EXPLICIT AFK signal (visibilitychange → hidden):
  afkConnections++
  if afkConnections == activeConnections:
    status = AFK
    broadcast change

EXPLICIT ACTIVE signal (visibilitychange → visible):
  afkConnections = max(0, afkConnections - 1)
  if status was AFK:
    status = ONLINE
    broadcast change

NO HEARTBEAT for >60s (checked by scheduled task every 30s):
  if activeConnections > 0 and status != AFK:
    status = AFK
    broadcast change
```

### Services

**PresenceService:**
- `connect(userId)` — increment connections, set ONLINE, broadcast
- `disconnect(userId)` — decrement connections, schedule OFFLINE if 0
- `heartbeat(userId)` — update lastHeartbeatAt, set ONLINE if was AFK
- `setStatus(userId, status)` — handle explicit AFK/ACTIVE signals with per-connection AFK tracking
- `getPresence(userId)` — return current status
- `getPresenceForUsers(List<Long> userIds)` — batch lookup for room member presence
- Scheduled task (every 30s): scan all entries, set AFK for users with no heartbeat in >60s

**UnreadService:**
- `markAsRead(userId, roomId, lastReadMessageId)` — upsert ReadReceipt
- `getUnreadCounts(userId)` — for each room the user is a member of, count messages where `created_at > last_read_at` (or all messages if no ReadReceipt exists). Returns `Map<UUID, Long>` (roomId -> count).
- Query: `SELECT m.room_id, COUNT(*) FROM messages m JOIN chat_room_members crm ON crm.room_id = m.room_id WHERE crm.user_id = :userId AND m.created_at > COALESCE((SELECT rr.last_read_at FROM read_receipts rr WHERE rr.user_id = :userId AND rr.room_id = m.room_id), '1970-01-01') GROUP BY m.room_id HAVING COUNT(*) > 0`

**Extend WebSocketMessageController:**
- After broadcasting a new message to `/topic/rooms/{roomId}/messages`, no additional work needed — the frontend unread hook increments locally for non-active rooms.

### DTOs

**Requests:** `MarkAsReadRequest` (lastReadMessageId: UUID), `PresenceStatusRequest` (status: String — "AFK" or "ACTIVE")
**Responses:** `PresenceResponse` (userId: Long, username: String, status: String), `UnreadCountResponse` (roomId: UUID, count: Long), `RoomMemberPresenceResponse` (userId: Long, username: String, displayName: String, role: String, status: String)

## Frontend

### Components

- `PresenceIndicator.tsx` — green dot (ONLINE), yellow dot (AFK), gray dot (OFFLINE). Takes a `status` prop. Used in room lists, member lists, and DM entries.
- `UnreadBadge.tsx` — numeric badge component. Shows count (capped at "99+"). Takes a `count` prop.
- Extend `RoomList.tsx` — add `UnreadBadge` next to each room name. Sort rooms by most recent activity (rooms with unread messages bubble up). Add `PresenceIndicator` next to DM entries showing the other user's status.
- Extend `RoomHeader.tsx` — add `PresenceIndicator` next to member avatars/names for DM rooms.
- Extend `ChatArea.tsx` / member lists — add `PresenceIndicator` next to usernames where member presence is displayed.

### Hooks

**`usePresence.ts`:**
- Subscribe to `/topic/presence` on WebSocket connect
- Maintain `Map<number, PresenceStatus>` (userId -> status)
- Update map on incoming presence events
- Expose `getPresence(userId)` and the full map
- On mount: no bulk fetch needed — presence events arrive on connect; for room member presence, fetch via REST `GET /api/rooms/{roomId}/members/presence` when selecting a room

**`useUnread.ts`:**
- Fetch initial unread counts via `GET /api/rooms/unread` on mount
- Maintain `Map<string, number>` (roomId -> unreadCount)
- On new message in a non-selected room: increment count locally
- On selecting a room: call `POST /api/rooms/{roomId}/read` with the latest message ID, set count to 0
- Expose `unreadCounts` map and `markAsRead(roomId, messageId)` function

**Extend `useWebSocket.ts`:**
- On connect: start heartbeat interval (every 15s) sending to `/app/presence/heartbeat`
- On disconnect: clear heartbeat interval
- Add `visibilitychange` event listener:
  - `document.hidden === true` -> send to `/app/presence/status` with `{status: "AFK"}`
  - `document.hidden === false` -> send to `/app/presence/status` with `{status: "ACTIVE"}`
- Clean up listeners on unmount
- Expose `subscribeToPresence(callback)` for the presence hook to use

### State Management

- `usePresence` and `useUnread` hooks used in `ChatLayout.tsx`
- Presence map passed down to components that display presence indicators
- Unread counts passed to `RoomList` for badge rendering
- When `handleSelectRoom` is called: mark room as read (both API call and local state reset)
- When `handleNewMessage` fires for a non-selected room: increment unread count locally

### Multi-tab Handling

- Each tab connects independently via its own WebSocket connection
- Server counts connections per user (source of truth for presence)
- No `BroadcastChannel` coordination needed — server handles multi-connection logic
- Each tab sends its own heartbeats and visibility signals independently

### API Layer

- New `api/presence.ts` — `getRoomMemberPresence(roomId)`
- New `api/unread.ts` — `getUnreadCounts()`, `markAsRead(roomId, lastReadMessageId)`
- Extend `api/types.ts` — `PresenceStatus` (enum: ONLINE, AFK, OFFLINE), `PresenceEvent`, `UnreadCount`, `MarkAsReadRequest`

## Database Migrations

- `012-create-read-receipts.sql` — `read_receipts` table with unique constraint on (user_id, room_id), foreign keys to users, chat_rooms, and messages

## Key Decisions

1. **In-memory presence** (not DB) — fast reads, acceptable for single-server deployment; presence is ephemeral and lost on restart is fine since users reconnect immediately
2. **Each tab = independent WebSocket** — server counts connections per user; simpler than client-side tab coordination via BroadcastChannel
3. **Heartbeat interval: 15s**, AFK timeout: 60s no heartbeat, offline timeout: 30s after last disconnect — balances responsiveness vs. overhead
4. **AFK requires all connections AFK** — user is only AFK when every open tab reports hidden; one active tab keeps them ONLINE
5. **Unread counts via direct query** — `COUNT(*) FROM messages WHERE room_id = ? AND created_at > last_read_at` — acceptable for moderate scale; no denormalized counters needed
6. **Presence broadcast to global topic** — `/topic/presence` rather than per-room topics; simpler for clients that are in multiple rooms; clients filter by relevant user IDs
7. **30s offline grace period** — prevents flicker during page refreshes or brief disconnections

## Testing Strategy

Two separate plans will be created:

**Implementation Plan:** Backend: PresenceService (in-memory), WebSocketEventListener, PresenceController, ReadReceipt entity/repository, UnreadService, UnreadController, extend WebSocketMessageController. Frontend: usePresence hook, useUnread hook, extend useWebSocket with heartbeats/visibility, PresenceIndicator component, UnreadBadge component, extend RoomList/RoomHeader/ChatArea.

**Testing Plan:** Backend integration tests (JUnit 5 + Testcontainers) for unread count endpoints, mark-as-read, and presence REST endpoint. WebSocket integration tests for heartbeat handling and presence broadcasts. E2E tests (Playwright) for presence indicator visibility, unread badge behavior, and mark-as-read on room selection.
