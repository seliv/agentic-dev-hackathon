--liquibase formatted sql

--changeset chatapp:003-add-username-to-users
ALTER TABLE users ADD COLUMN username VARCHAR(32);

UPDATE users SET username = 'alice' WHERE email = 'alice@demo.com';
UPDATE users SET username = 'bob' WHERE email = 'bob@demo.com';
UPDATE users SET username = 'carol' WHERE email = 'carol@demo.com';
UPDATE users SET username = 'dave' WHERE email = 'dave@demo.com';
UPDATE users SET username = 'eve' WHERE email = 'eve@demo.com';
UPDATE users SET username = 'frank' WHERE email = 'frank@demo.com';

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username);

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
