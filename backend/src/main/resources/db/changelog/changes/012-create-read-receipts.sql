--liquibase formatted sql

--changeset chatapp:012-create-read-receipts
CREATE TABLE read_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    last_read_message_id UUID NOT NULL REFERENCES messages(id),
    last_read_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT read_receipts_unique UNIQUE (user_id, room_id)
);
