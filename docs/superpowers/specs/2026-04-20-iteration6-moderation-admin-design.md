# Iteration 6: Moderation & Administration — Design Spec

**Goal:** Room owners and admins can fully moderate rooms — manage members, admins, bans, room settings, and delete rooms. Public room catalog with search.

## Architecture

One new entity (`RoomBan`) and one updated enum (`MemberRole` gains `ADMIN`). A new `ModerationService` handles all permission-checked moderation actions (ban, role changes). `ChatRoomService` is extended for room settings editing and room deletion. `MessageService` is extended to allow admin deletion. A new `ModerationController` exposes moderation REST endpoints. Frontend adds a `ManageRoomModal` with tabs for members, admins, bans, and settings, plus extends existing components with admin actions.

## Backend

### Entities

**MemberRole enum** — add `ADMIN`:
```
OWNER, ADMIN, MEMBER
```

**RoomBan (new):**
- `id` — BIGSERIAL, primary key
- `room_id` — UUID, FK to chat_rooms.id ON DELETE CASCADE, NOT NULL
- `user_id` — BIGINT, FK to users.id, NOT NULL
- `banned_by_id` — BIGINT, FK to users.id, NOT NULL
- `reason` — TEXT, nullable
- `created_at` — TIMESTAMP, NOT NULL
- UNIQUE constraint on (room_id, user_id)

### Permission Hierarchy

All permission checks live in `ModerationService`. The hierarchy:

- **OWNER** can: promote/demote to ADMIN, ban/kick anyone (except self), edit room settings, delete room
- **ADMIN** can: ban/kick MEMBERs and other ADMINs (not OWNER), delete any message in the room
- **ADMIN cannot**: ban/kick OWNER, demote OWNER, delete room
- **MEMBER** can: no moderation actions

Validation rule: the caller's role must outrank or equal the target's role, except OWNER is always protected. Specifically:
- OWNER can act on ADMIN and MEMBER
- ADMIN can act on ADMIN and MEMBER (but not OWNER)

### REST Endpoints (ModerationController)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| PUT | `/api/rooms/{roomId}/members/{userId}/role` | OWNER | Set role to ADMIN or MEMBER |
| POST | `/api/rooms/{roomId}/bans` | OWNER/ADMIN | Ban user (removes from room, prevents rejoin). Body: `{userId, reason?}` |
| DELETE | `/api/rooms/{roomId}/bans/{userId}` | OWNER/ADMIN | Unban user |
| GET | `/api/rooms/{roomId}/bans` | OWNER/ADMIN | List banned users |

### Extended Endpoints (ChatRoomController)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| PUT | `/api/rooms/{roomId}` | OWNER/ADMIN | Edit room name, description |
| DELETE | `/api/rooms/{roomId}` | OWNER | Delete room with cascade |
| DELETE | `/api/rooms/{roomId}/members/{userId}` | OWNER/ADMIN | Kick member (creates ban record) |

### Extended Endpoints (MessageController)

- `DELETE /api/rooms/{roomId}/messages/{messageId}` — extend existing to allow ADMIN role (currently only sender and OWNER)

### WebSocket Additions

When a user is banned/kicked, the server sends a notification to the banned user's personal queue:
- `/queue/notifications` — payload: `{type: "ROOM_BANNED", data: {roomId, roomName}}`

When a room is deleted, the server broadcasts to the room topic:
- `/topic/rooms/{roomId}/events` — payload: `{type: "ROOM_DELETED", data: {roomId}}`

When a member's role changes, broadcast to the room:
- `/topic/rooms/{roomId}/events` — payload: `{type: "MEMBER_ROLE_CHANGED", data: {userId, username, newRole}}`

### Services

**ModerationService (new):**
- `banUser(roomId, targetUserId, bannedByUserId, reason)` — validate permissions, remove member, create RoomBan, send WebSocket notification
- `unbanUser(roomId, targetUserId, callerUserId)` — validate permissions, delete RoomBan
- `getBannedUsers(roomId, callerUserId)` — validate caller is OWNER/ADMIN, return list
- `changeRole(roomId, targetUserId, newRole, callerUserId)` — validate OWNER, update role, broadcast event
- `kickMember(roomId, targetUserId, callerUserId)` — delegates to `banUser` (kick = ban)

**ChatRoomService (extended):**
- `updateRoom(roomId, name, description, callerUserId)` — validate OWNER/ADMIN, update fields
- `deleteRoom(roomId, callerUserId)` — validate OWNER, delete room (cascade handles related data), clean up attachment files from disk
- `joinRoom` — extend to check RoomBan before allowing join

**MessageService (extended):**
- `deleteMessage` — extend permission check to include ADMIN role callers

### Repositories

**RoomBanRepository (new):**
- `findByRoomId(UUID roomId)` — list bans for a room
- `findByRoomIdAndUserId(UUID roomId, Long userId)` — check if user is banned
- `existsByRoomIdAndUserId(UUID roomId, Long userId)` — quick ban check
- `deleteByRoomIdAndUserId(UUID roomId, Long userId)` — unban

### DTOs

**Requests:**
- `BanUserRequest` — `userId` (Long), `reason` (String, optional)
- `UpdateRoomRequest` — `name` (String, optional), `description` (String, optional)
- `ChangeRoleRequest` — `role` (String: "ADMIN" or "MEMBER")

**Responses:**
- `RoomBanResponse` — `id`, `roomId`, `userId`, `username`, `displayName`, `bannedById`, `bannedByUsername`, `reason`, `createdAt`

## Frontend

### Components

**ManageRoomModal.tsx (new):** Tabbed modal opened from RoomHeader. Tabs:
- **Members** — list all members with role badge, action buttons per row:
  - For OWNER viewing: "Make Admin" / "Remove Admin", "Ban" buttons on each member
  - For ADMIN viewing: "Ban" button on MEMBERs and other ADMINs (not OWNER)
- **Banned Users** — list banned users with banned-by info, "Unban" button
- **Settings** — edit room name and description (OWNER/ADMIN), "Delete Room" button with confirmation modal (OWNER only)

**Extend RoomHeader.tsx:** Add "Manage Room" gear icon button, visible only to OWNER and ADMIN.

**Extend MessageBubble.tsx:** Show delete action for ADMIN role users on other members' messages.

**Extend RoomBrowser.tsx:** The existing public room browser already has search and pagination. No changes needed.

**Extend ChatLayout.tsx:** Handle `ROOM_BANNED` notification — remove room from sidebar, redirect if currently viewing that room. Handle `ROOM_DELETED` event — same behavior.

**Extend RoomList.tsx:** Handle room removal when banned/kicked/room deleted.

### API Layer

**New `api/moderation.ts`:**
- `banUser(roomId, userId, reason?)` — POST `/api/rooms/{roomId}/bans`
- `unbanUser(roomId, userId)` — DELETE `/api/rooms/{roomId}/bans/{userId}`
- `getBannedUsers(roomId)` — GET `/api/rooms/{roomId}/bans`
- `changeRole(roomId, userId, role)` — PUT `/api/rooms/{roomId}/members/{userId}/role`
- `kickMember(roomId, userId)` — DELETE `/api/rooms/{roomId}/members/{userId}`

**Extend `api/rooms.ts`:**
- `updateRoom(roomId, name?, description?)` — PUT `/api/rooms/{roomId}`
- `deleteRoom(roomId)` — DELETE `/api/rooms/{roomId}`

**Extend `api/types.ts`:**
- `RoomBan` — `id`, `roomId`, `userId`, `username`, `displayName`, `bannedById`, `bannedByUsername`, `reason`, `createdAt`
- `BanUserRequest` — `userId`, `reason?`
- `UpdateRoomRequest` — `name?`, `description?`
- `ChangeRoleRequest` — `role`
- `MemberRoleChangedEvent`, `RoomDeletedEvent`, `RoomBannedNotification`

### WebSocket Event Handling

Extend the existing `useWebSocket` hook and `ChatLayout` to handle new event types:
- `ROOM_DELETED` on `/topic/rooms/{roomId}/events` — remove room from state, navigate away
- `MEMBER_ROLE_CHANGED` on `/topic/rooms/{roomId}/events` — update member list, adjust UI permissions
- `ROOM_BANNED` on `/queue/notifications` — remove room, show notification toast

## Database Migrations

**015-create-room-bans.sql:**
```sql
CREATE TABLE room_bans (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    banned_by_id BIGINT NOT NULL REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (room_id, user_id)
);
```

**016-add-cascade-deletes.sql:**
Add `ON DELETE CASCADE` to all foreign keys referencing `chat_rooms.id`:
- `chat_room_members.room_id`
- `messages.room_id`
- `read_receipts.room_id`
- `room_invitations.room_id`

Also add `ON DELETE CASCADE` to `attachments.message_id` FK referencing `messages.id`, so that deleting messages (via room cascade) also cascades to attachments.

This requires dropping and re-creating the FK constraints. The `room_bans` table already has cascade from migration 015.

## Key Decisions

1. **Kick = Ban** — every member removal creates a ban record preventing rejoin. Simplifies the model; unban is available if the removal was unintended.
2. **Admins can act on other admins** (but not owner) — gives admins meaningful power without risking owner authority.
3. **Hard cascade delete** for rooms — `ON DELETE CASCADE` on all FKs referencing `chat_rooms.id`. Application layer additionally cleans up attachment files from disk before deleting the room record.
4. **Permission checks in service layer** — `ModerationService` validates caller role vs target role on every action. No AOP or annotation-based checks.
5. **Room search already exists** — `GET /api/rooms/public?search=&page=&size=` with ILIKE is already implemented. No additional search infrastructure needed.
6. **WebSocket notifications for ban/kick/delete** — ensures banned users see immediate UI feedback without polling.
7. **ManageRoomModal as single entry point** — one modal with tabs rather than scattered moderation controls.
8. **File cleanup on room deletion** — query attachments for the room before cascade delete, remove files from disk, then delete the room record. Wrapped in a transaction with file cleanup best-effort (files left on disk if cleanup fails won't break the app).

## Testing Strategy

Two separate plans will be created:

**Implementation Plan:** Backend entity/enum changes, migrations, repository, ModerationService, extended services, ModerationController, extended controllers, WebSocket events, then frontend components and API layer.

**Testing Plan:** Backend integration tests (JUnit 5 + Testcontainers) for all moderation endpoints, permission hierarchy enforcement, ban/kick flows, room deletion cascade, and admin message deletion. E2E tests (Playwright) for the ManageRoomModal UI, ban/kick flows, role management, room settings editing, and room deletion.
