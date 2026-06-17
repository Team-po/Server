CREATE TABLE chat_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_chat_room_project_group
        FOREIGN KEY (project_group_id) REFERENCES project_group (id),
    CONSTRAINT uq_chat_room_project_group
        UNIQUE (project_group_id)
);

CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,

    CONSTRAINT fk_chat_message_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_message_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_message_room_id_id
    ON chat_message (chat_room_id, id);

CREATE INDEX idx_chat_message_sender_user_id
    ON chat_message (sender_user_id);

CREATE TABLE chat_read_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_chat_read_state_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_read_state_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_read_state_message
        FOREIGN KEY (last_read_message_id) REFERENCES chat_message (id),
    CONSTRAINT uq_chat_read_state_room_user
        UNIQUE (chat_room_id, user_id)
);
