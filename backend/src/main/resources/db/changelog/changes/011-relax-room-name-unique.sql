--liquibase formatted sql

--changeset chatapp:011-relax-room-name-unique
ALTER TABLE chat_rooms DROP CONSTRAINT chat_rooms_name_unique;

CREATE UNIQUE INDEX chat_rooms_name_unique_public
    ON chat_rooms (name)
    WHERE type = 'PUBLIC';
