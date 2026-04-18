--liquibase formatted sql

--changeset chatapp:006-create-chat-room-members
CREATE TABLE chat_room_members (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_room_members_unique UNIQUE (room_id, user_id)
);
