--liquibase formatted sql

--changeset chatapp:016-add-cascade-deletes

-- chat_room_members.room_id
ALTER TABLE chat_room_members DROP CONSTRAINT IF EXISTS chat_room_members_room_id_fkey;
ALTER TABLE chat_room_members ADD CONSTRAINT chat_room_members_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- messages.room_id
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_room_id_fkey;
ALTER TABLE messages ADD CONSTRAINT messages_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- read_receipts.room_id
ALTER TABLE read_receipts DROP CONSTRAINT IF EXISTS read_receipts_room_id_fkey;
ALTER TABLE read_receipts ADD CONSTRAINT read_receipts_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- room_invitations.room_id
ALTER TABLE room_invitations DROP CONSTRAINT IF EXISTS room_invitations_room_id_fkey;
ALTER TABLE room_invitations ADD CONSTRAINT room_invitations_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE;

-- attachments.message_id (cascade from message deletion)
ALTER TABLE attachments DROP CONSTRAINT IF EXISTS attachments_message_id_fkey;
ALTER TABLE attachments ADD CONSTRAINT attachments_message_id_fkey
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE;
