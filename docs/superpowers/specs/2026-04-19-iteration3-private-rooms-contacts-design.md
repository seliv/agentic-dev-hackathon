# Iteration 3: Private Rooms, Contacts & Personal Messaging — Design Spec

**Goal:** Users can create private (invite-only) rooms, manage a friends list, block users, and have direct message conversations.

## Architecture

Three new entities (`RoomInvitation`, `Friendship`, `UserBlock`) plus extensions to `RoomType` (add `PRIVATE`, `DIRECT`). Four new controllers following one-controller-per-domain pattern: `RoomInvitationController`, `FriendshipController`, `BlockController`, `DirectMessageController`. Extend existing `ChatRoomController` to support `type: PRIVATE`. User search via existing `UserController`. Real-time notification delivery via WebSocket user-specific queue (`/queue/notifications`). Frontend adds contacts panel, user search modal, invitation/friend request lists, and splits the room sidebar into Rooms and DMs sections.

## Backend

### Entities

**RoomType (extend):** Add `PRIVATE` and `DIRECT` enum values.

**ChatRoom (extend):** Relax unique constraint on `name` — only PUBLIC rooms need unique names. DIRECT rooms get auto-generated names (`dm-{min(idA,idB)}-{max(idA,idB)}`).

**CreateRoomRequest (extend):** Add optional `type` field (defaults to `PUBLIC`).

**RoomInvitation:**
- `id` — BIGSERIAL, primary key
- `room_id` — UUID, FK to chat_rooms.id, NOT NULL
- `inviter_id` — BIGINT, FK to users.id, NOT NULL
- `invitee_id` — BIGINT, FK to users.id, NOT NULL
- `status` — VARCHAR (enum: PENDING, ACCEPTED, DECLINED), default PENDING
- `created_at`, `updated_at` — TIMESTAMP
- Unique partial index on (room_id, invitee_id) WHERE status = 'PENDING'

**Friendship:**
- `id` — BIGSERIAL, primary key
- `requester_id` — BIGINT, FK to users.id, NOT NULL
- `addressee_id` — BIGINT, FK to users.id, NOT NULL
- `status` — VARCHAR (enum: PENDING, ACCEPTED, DECLINED), default PENDING
- `created_at`, `updated_at` — TIMESTAMP
- UNIQUE constraint on (requester_id, addressee_id) — reverse-direction duplicates prevented at service layer (check both directions before insert)
- Index on addressee_id for incoming request queries
- Query friends: `WHERE (requester_id = ? OR addressee_id = ?) AND status = ACCEPTED`

**UserBlock:**
- `id` — BIGSERIAL, primary key
- `blocker_id` — BIGINT, FK to users.id, NOT NULL
- `blocked_id` — BIGINT, FK to users.id, NOT NULL
- `created_at` — TIMESTAMP
- UNIQUE constraint on (blocker_id, blocked_id)
- Index on blocked_id for bidirectional lookups

### REST Endpoints

**ChatRoomController (extend):**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/rooms` | Required | Extend — accept `type: PRIVATE` in request body |

Private rooms skip unique name validation. Private rooms don't appear in `GET /api/rooms/public`.

**RoomInvitationController:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/rooms/{roomId}/invitations` | Member | Invite user to private room (body: `{userId}`) |
| GET | `/api/users/me/invitations` | Required | List pending invitations for current user |
| POST | `/api/invitations/{id}/accept` | Invitee | Accept invitation (auto-joins room) |
| POST | `/api/invitations/{id}/decline` | Invitee | Decline invitation |

**FriendshipController:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/friends/request` | Required | Send friend request (body: `{userId}`) |
| GET | `/api/friends` | Required | List accepted friends |
| GET | `/api/friends/requests` | Required | Pending requests (incoming + outgoing) |
| POST | `/api/friends/{id}/accept` | Addressee | Accept friend request |
| POST | `/api/friends/{id}/decline` | Addressee | Decline friend request |
| DELETE | `/api/friends/{id}` | Either party | Remove friend |

**BlockController:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/users/{userId}/block` | Required | Block user |
| DELETE | `/api/users/{userId}/block` | Required | Unblock user |
| GET | `/api/users/me/blocks` | Required | List blocked users |

**DirectMessageController:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/direct-messages/{userId}` | Required | Get or create DM room (requires friendship, rejects if blocked) |

**UserController (extend):**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/search?q=...` | Required | Search users by username/display name (ILIKE) |

### WebSocket

- Subscribe `/queue/notifications` — user-specific queue for real-time delivery of friend requests and room invitations
- Uses `SimpMessagingTemplate.convertAndSendToUser()` with user ID as principal name
- Notification payload: `{type: "FRIEND_REQUEST" | "ROOM_INVITATION", data: {...}}`

### Services

**RoomInvitationService:**
- `inviteUser(roomId, inviterId, inviteeId)` — validates: room is PRIVATE, inviter is member, invitee is not already member, no pending invite exists, invitee hasn't blocked inviter. Saves invitation, sends WebSocket notification.
- `acceptInvitation(invitationId, userId)` — validates invitee matches, status is PENDING. Updates to ACCEPTED, adds invitee as MEMBER.
- `declineInvitation(invitationId, userId)` — validates invitee matches, status is PENDING. Updates to DECLINED.
- `getPendingInvitations(userId)` — returns pending invitations for user.

**FriendshipService:**
- `sendRequest(requesterId, addresseeId)` — validates: not self, no existing friendship row (either direction), addressee hasn't blocked requester. Creates PENDING, sends WebSocket notification.
- `acceptRequest(friendshipId, userId)` — validates addressee matches, status is PENDING. Updates to ACCEPTED.
- `declineRequest(friendshipId, userId)` — validates addressee matches, status is PENDING. Updates to DECLINED.
- `removeFriend(friendshipId, userId)` — validates user is requester or addressee, status is ACCEPTED. Deletes row.
- `getFriends(userId)` — list accepted friendships.
- `getPendingRequests(userId)` — incoming + outgoing pending.
- `areFriends(userIdA, userIdB)` — boolean check, used by DM creation.

**BlockService:**
- `blockUser(blockerId, blockedId)` — validates not self, creates UserBlock. Auto-removes any existing friendship.
- `unblockUser(blockerId, blockedId)` — deletes UserBlock row.
- `getBlockedUsers(blockerId)` — list blocks.
- `isBlocked(userIdA, userIdB)` — bidirectional check (either user blocked the other).

**ChatRoomService (extend):**
- `createRoom` — support `type: PRIVATE` (no public catalog, relaxed name uniqueness)
- `findOrCreateDirectRoom(userIdA, userIdB)` — finds existing DIRECT room for the pair, or creates one with both users as members. Room name: `dm-{min(idA,idB)}-{max(idA,idB)}`.

**MessageService (extend):**
- `sendMessage` — before saving, if room is DIRECT, check if either user has blocked the other. Reject with 403 if blocked.

### DTOs

**Requests:** `InviteUserRequest` (userId), `FriendRequestRequest` (userId)
**Responses:** `RoomInvitationResponse` (id, roomId, roomName, inviterUsername, status, createdAt), `FriendshipResponse` (id, friendUserId, friendUsername, friendDisplayName, status, direction, createdAt), `UserBlockResponse` (id, blockedUserId, blockedUsername, createdAt), `UserSearchResponse` (id, username, displayName)

## Frontend

### Components

- Extend `RoomList.tsx` — split into two sections: "Rooms" (PUBLIC/PRIVATE) and "Direct Messages" (DIRECT). DMs show the other user's display name instead of `#room-name`. Add "Contacts" button alongside Browse/Create.
- Extend `CreateRoomModal.tsx` — add room type toggle (Public / Private).
- Extend `RoomHeader.tsx` — "Invite" button for PRIVATE rooms (visible to all members). Show other user's name for DIRECT rooms.
- `ContactsPanel.tsx` — drawer/modal with three tabs: Friends (list with "Send DM" and "Remove" actions), Requests (incoming with accept/decline, outgoing with cancel), Blocked (list with unblock button).
- `UserSearchModal.tsx` — debounced ILIKE search via `/api/users/search`. Results show username + display name with contextual action buttons: Add Friend, Invite to Room, Send DM.
- `FriendRequestList.tsx` — used inside ContactsPanel, renders incoming/outgoing with accept/decline/cancel.
- `InvitationList.tsx` — pending room invitations, shown in sidebar or as notification dropdown. Accept joins room and selects it; decline removes item.
- `InviteToRoomModal.tsx` — select a friend to invite to a private room. Lists friends not already members. Triggered from RoomHeader.
- `UserProfilePopover.tsx` — click username in messages/member lists for popover with Add Friend / Send DM / Block actions. Shows friendship status contextually.

### State Management

- Chat state in `ChatLayout` gains a notification listener: subscribe to `/queue/notifications` on WebSocket connect, update friend requests / invitations lists on incoming events.
- No new context needed — friend/block state is fetched on demand when ContactsPanel or UserProfilePopover opens.

### API Layer

- New `api/friends.ts` — `sendRequest`, `getFriends`, `getPendingRequests`, `acceptRequest`, `declineRequest`, `removeFriend`
- New `api/blocks.ts` — `blockUser`, `unblockUser`, `getBlockedUsers`
- New `api/invitations.ts` — `inviteToRoom`, `getMyInvitations`, `acceptInvitation`, `declineInvitation`
- New `api/directMessages.ts` — `getOrCreateDMRoom`
- Extend `api/users.ts` — add `searchUsers`
- Extend `api/types.ts` — `RoomInvitation`, `Friendship`, `UserBlock`, `UserSearchResult`, `FriendRequestRequest`, `InviteUserRequest`, `NotificationPayload`

## Database Migrations

- `008-create-room-invitations.sql` — `room_invitations` table with unique partial index on (room_id, invitee_id) WHERE status = 'PENDING'
- `009-create-friendships.sql` — `friendships` table with unique (requester_id, addressee_id), index on addressee_id
- `010-create-user-blocks.sql` — `user_blocks` table with unique (blocker_id, blocked_id), index on blocked_id
- `011-relax-room-name-unique.sql` — replace unique constraint on chat_rooms.name with partial unique index WHERE type = 'PUBLIC'

## Key Decisions

1. **DMs as DIRECT room type** — reuses messages table and WebSocket infra; no duplicate messaging system
2. **Single friendship row** (not symmetric pair) — query with `WHERE requester_id = ? OR addressee_id = ?`; simpler than maintaining two rows
3. **Block enforcement:** server-side in DMs (reject message send with 403), client-side in group rooms (hide messages from blocked users via frontend filtering)
4. **DM requires friendship** — enforced in `findOrCreateDirectRoom`; if friendship removed, existing DM room stays but no new DMs can be initiated until re-friended; messages in existing room still work unless blocked
5. **Partial unique index on room name** — only PUBLIC rooms need unique names; PRIVATE rooms can have duplicate names; DIRECT rooms use `dm-{minId}-{maxId}` convention
6. **Notifications via `/queue/notifications`** — STOMP user-specific queue, no polling; uses existing WebSocket session handshake for auth
7. **Block cascades to friendship** — blocking a user auto-removes any existing friendship

## Testing Strategy

Two separate plans will be created:

**Implementation Plan:** Migrations, entities, repositories, services, controllers, WebSocket notifications, then frontend components, API modules, state management.

**Testing Plan:** Backend integration tests (JUnit 5 + Testcontainers) for all new endpoints and block/friendship/invitation logic. E2E tests (Playwright) for friend request flow, DM creation, private room invitation flow, block behavior.
