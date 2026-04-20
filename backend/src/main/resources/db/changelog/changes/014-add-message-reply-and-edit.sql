--liquibase formatted sql

--changeset chatapp:014-add-message-reply-and-edit
ALTER TABLE messages ADD COLUMN reply_to_id UUID REFERENCES messages(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMP;
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_messages_reply_to_id ON messages(reply_to_id);
