--liquibase formatted sql

--changeset chatapp:015-create-room-bans
CREATE TABLE room_bans (
    id BIGSERIAL PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    banned_by_id BIGINT NOT NULL REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (room_id, user_id)
);

CREATE INDEX idx_room_bans_room_id ON room_bans(room_id);
