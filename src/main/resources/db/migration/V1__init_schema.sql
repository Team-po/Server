CREATE TABLE users
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      VARCHAR(255),
    password     VARCHAR(255),
    profileImage VARCHAR(255),
    description  VARCHAR(255),
    email        VARCHAR(255),
    nickname     VARCHAR(255),
    temperature  INT,
    level        INT
);
