-- V1: Initial schema
-- Derived from JPA entities (Hibernate 6 / Quarkus 3.x naming conventions)
-- Column names use snake_case (CamelCaseToUnderscoresNamingStrategy)

-- Shared sequence used by Hibernate for all entity IDs
CREATE SEQUENCE IF NOT EXISTS hibernate_sequence START WITH 1 INCREMENT BY 50;

-- ============================================================
-- organization
-- ============================================================
CREATE TABLE organization
(
    id           BIGINT                      NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    name         VARCHAR(255)                NOT NULL UNIQUE,
    slug         VARCHAR(255)                NOT NULL UNIQUE,
    display_name VARCHAR(255),
    description  VARCHAR(255),
    active       BOOLEAN                     NOT NULL             DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE    NOT NULL
);

-- ============================================================
-- member
-- ============================================================
CREATE TABLE member
(
    id                   BIGINT       NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    first_name           VARCHAR(255),
    last_name            VARCHAR(255),
    email                VARCHAR(255),
    phone                VARCHAR(255),
    user_name            VARCHAR(255) UNIQUE,
    organization_id      BIGINT       NOT NULL REFERENCES organization (id),
    keycloak_user_id     VARCHAR(255) UNIQUE,
    invited_by_member_id BIGINT,
    invite_type          VARCHAR(20),
    joined_at            TIMESTAMP WITH TIME ZONE
);

-- ============================================================
-- bommel
-- ============================================================
CREATE TABLE bommel
(
    id                    BIGINT       NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    icon                  VARCHAR(255),
    title                 VARCHAR(255),
    parent_id             BIGINT REFERENCES bommel (id),
    responsible_member_id BIGINT REFERENCES member (id),
    organization_id       BIGINT       NOT NULL REFERENCES organization (id)
);

-- ============================================================
-- tag
-- ============================================================
CREATE TABLE tag
(
    id              BIGINT       NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    organization_id BIGINT       NOT NULL REFERENCES organization (id),
    name            VARCHAR(255) NOT NULL,
    UNIQUE (organization_id, name)
);

-- ============================================================
-- tradeparty
-- ============================================================
CREATE TABLE tradeparty
(
    id                 BIGINT       NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    organization_id    BIGINT       NOT NULL REFERENCES organization (id),
    name               VARCHAR(255),
    country            VARCHAR(255),
    state              VARCHAR(255),
    city               VARCHAR(255),
    zip_code           VARCHAR(255),
    street             VARCHAR(255),
    additional_address VARCHAR(255),
    tax_id             VARCHAR(255),
    vat_id             VARCHAR(255)
);

-- ============================================================
-- document
-- ============================================================
CREATE TABLE document
(
    id                BIGINT                   NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    organization_id   BIGINT                   NOT NULL REFERENCES organization (id),
    bommel_id         BIGINT REFERENCES bommel (id),
    sender_id         BIGINT REFERENCES tradeparty (id),
    recipient_id      BIGINT REFERENCES tradeparty (id),
    name              VARCHAR(255),
    total             NUMERIC                  NOT NULL,
    currency_code     VARCHAR(255),
    transaction_time  TIMESTAMP WITH TIME ZONE,
    privately_paid    BOOLEAN                           DEFAULT FALSE,
    total_tax         NUMERIC,
    file_key          VARCHAR(255),
    file_name         VARCHAR(255),
    file_content_type VARCHAR(255),
    file_size         BIGINT,
    analysis_status   VARCHAR(255),
    analysis_error    VARCHAR(255),
    extraction_source VARCHAR(255),
    document_status   VARCHAR(255),
    flow_id           VARCHAR(255),
    uploaded_by       VARCHAR(255),
    analyzed_by       VARCHAR(255),
    reviewed_by       VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ============================================================
-- document_tag
-- ============================================================
CREATE TABLE document_tag
(
    id          BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    document_id BIGINT NOT NULL REFERENCES document (id),
    tag_id      BIGINT NOT NULL REFERENCES tag (id),
    source      VARCHAR(255),
    UNIQUE (document_id, tag_id)
);

-- ============================================================
-- transactionrecord
-- ============================================================
CREATE TABLE transactionrecord
(
    id               BIGINT                   NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    organization_id  BIGINT                   NOT NULL REFERENCES organization (id),
    bommel_id        BIGINT REFERENCES bommel (id),
    document_id      BIGINT REFERENCES document (id),
    sender_id        BIGINT REFERENCES tradeparty (id),
    recipient_id     BIGINT REFERENCES tradeparty (id),
    uploader         VARCHAR(255)             NOT NULL,
    total            NUMERIC                  NOT NULL,
    privately_paid   BOOLEAN                  NOT NULL             DEFAULT FALSE,
    transaction_time TIMESTAMP WITH TIME ZONE,
    name             VARCHAR(255),
    currency_code    VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ============================================================
-- transaction_tag
-- ============================================================
CREATE TABLE transaction_tag
(
    id                  BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    transaction_record_id BIGINT NOT NULL REFERENCES transactionrecord (id),
    tag_id              BIGINT NOT NULL REFERENCES tag (id),
    source              VARCHAR(255),
    UNIQUE (transaction_record_id, tag_id)
);

-- ============================================================
-- invitation
-- ============================================================
CREATE TABLE invitation
(
    id              BIGINT                   NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    token           VARCHAR(36)              NOT NULL UNIQUE,
    email           VARCHAR(255)             NOT NULL,
    organization_id BIGINT REFERENCES organization (id),
    role            VARCHAR(255)             NOT NULL,
    status          VARCHAR(255)             NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    invited_by_id   BIGINT                   NOT NULL REFERENCES member (id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at     TIMESTAMP WITH TIME ZONE
);

-- ============================================================
-- auditlogentry
-- ============================================================
CREATE TABLE auditlogentry
(
    id              BIGINT                   NOT NULL PRIMARY KEY DEFAULT nextval('hibernate_sequence'),
    organization_id BIGINT                   NOT NULL REFERENCES organization (id),
    username        VARCHAR(255),
    timestamp       TIMESTAMP WITH TIME ZONE NOT NULL,
    task_name       VARCHAR(255),
    details         VARCHAR(4000),
    entity_name     VARCHAR(255),
    entity_id       VARCHAR(255)
);
