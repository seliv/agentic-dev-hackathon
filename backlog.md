# Backlog: Iteration Plan vs Requirements Gap Analysis

Reviewed 2026-04-18. These items are deviations between `iterations.md` and `requirements.md` to address later.

## Critical

- [ ] **Password reset not planned** — Req 2.1.4 requires password reset; wireframe shows "Forgot password?" form. Iter 1 defers it entirely.
- [ ] **Account deletion cascade incomplete** — Req 2.1.5: deleting account must delete owned rooms + their messages/files, remove membership in other rooms. Iter 1 only adds `deleted_at` soft-delete, no cascade logic.
- [ ] **Admin message deletion misplaced** — Req 2.5.5: admins can delete messages. Iter 5 delete endpoint only allows sender or room owner. Admin permission not added until Iter 6.

## Medium

- [ ] **Sidebar on wrong side** — Req 4.1.1 and wireframe: rooms/contacts on the right. Iter 2 puts them on the left.
- [ ] **Accordion room compacting missing** — Req 4.1.1: room list compacts in accordion style after entering a room. No iteration mentions this.
- [ ] **Admin demotion by admins** — Req 2.4.7: admins can demote other admins (except owner). Iter 6 restricts role changes to owner only.
- [ ] **Friend request optional text** — Req 2.3.2: friend request may include optional text. Iter 4 endpoint doesn't include a text field.
- [ ] **Attachment comment field** — Req 2.6.3: user may add optional comment to attachment. Iter 5 entity has no comment field.
- [ ] **Public room catalog built twice** — Iter 2 builds basic `RoomBrowser.tsx`, Iter 6 rebuilds as `RoomCatalog.tsx` with search. Consider building search from the start.
- [ ] **Room name uniqueness on rename** — Req 2.4.2: room names must be unique. Iter 6 allows editing room name but doesn't mention uniqueness validation.

## Low

- [ ] **Session browser/IP details missing** — Req 2.2.4: sessions should show browser/IP details. Iter 1 `SessionResponse` has no user-agent or IP fields.
- [ ] **Unread indicators for DMs** — Iter 3 builds unread indicators before DMs exist in Iter 4. Will need extension.
- [ ] **Top nav bar incomplete** — Wireframe shows full nav (Public Rooms, Private Rooms, Contacts, Sessions, Profile, Sign out). Not fully specified across iterations.
