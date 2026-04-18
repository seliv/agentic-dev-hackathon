--liquibase formatted sql

--changeset chatapp:002-insert-demo-data
-- Insert demo users (passwords are BCrypt hashed for "password")
INSERT INTO users (email, password, display_name, created_at, updated_at)
VALUES
    ('alice@demo.com', '$2a$10$6Y.fT4WDdyS2U75h.t6Acue08Xcbxi2G1y/O9Ha9CjgVxqSChHNyW', 'Alice Demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('bob@demo.com', '$2a$10$6Y.fT4WDdyS2U75h.t6Acue08Xcbxi2G1y/O9Ha9CjgVxqSChHNyW', 'Bob Demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('carol@demo.com', '$2a$10$6Y.fT4WDdyS2U75h.t6Acue08Xcbxi2G1y/O9Ha9CjgVxqSChHNyW', 'Carol Demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('dave@demo.com', '$2a$10$6Y.fT4WDdyS2U75h.t6Acue08Xcbxi2G1y/O9Ha9CjgVxqSChHNyW', 'Dave Demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('eve@demo.com', '$2a$10$6Y.fT4WDdyS2U75h.t6Acue08Xcbxi2G1y/O9Ha9CjgVxqSChHNyW', 'Eve Demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('frank@demo.com', '$2a$10$6Y.fT4WDdyS2U75h.t6Acue08Xcbxi2G1y/O9Ha9CjgVxqSChHNyW', 'Frank Demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
