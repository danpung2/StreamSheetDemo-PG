-- ============================================
-- PG Demo 초기 스키마
-- PG Demo Initial Schema
-- ============================================

-- 본사 테이블 / Headquarters table
CREATE TABLE IF NOT EXISTS headquarters (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    headquarters_code   VARCHAR(20) UNIQUE NOT NULL,  -- HQ-XXXXX
    name                VARCHAR(100) NOT NULL,
    business_number     VARCHAR(20) NOT NULL,
    contract_type       VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 본사명 중복 금지(대소문자 무시) / Headquarters name must be unique (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS uq_headquarters_name_ci ON headquarters (lower(name));

-- 업체(가맹점) 테이블 / Merchant table
CREATE TABLE IF NOT EXISTS merchant (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_code       VARCHAR(30) UNIQUE NOT NULL,  -- MER-XXXXX-NNN
    headquarters_id     UUID REFERENCES headquarters(id),
    name                VARCHAR(100) NOT NULL,
    store_number        INTEGER,
    store_type          VARCHAR(20) NOT NULL,  -- FRANCHISE, DIRECT
    business_type       VARCHAR(30) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    contract_start_date DATE NOT NULL,
    contract_end_date   DATE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 동일 본사 내 가맹점명 중복 금지(대소문자 무시) / Merchant name must be unique within headquarters (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_hq_name_ci
    ON merchant (headquarters_id, lower(name))
    WHERE headquarters_id IS NOT NULL;

-- 결제 트랜잭션 테이블 / Payment transaction table
CREATE TABLE IF NOT EXISTS payment_transaction (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         UUID NOT NULL REFERENCES merchant(id),
    order_id            VARCHAR(50) NOT NULL,
    amount              BIGINT NOT NULL,
    payment_method      VARCHAR(20) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    requested_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    completed_at        TIMESTAMP,
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 환불 트랜잭션 테이블 / Refund transaction table
CREATE TABLE IF NOT EXISTS refund_transaction (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID NOT NULL REFERENCES payment_transaction(id),
    refund_amount       BIGINT NOT NULL,
    refund_reason       VARCHAR(255),
    status              VARCHAR(30) NOT NULL,
    requested_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    completed_at        TIMESTAMP,
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 관리자 사용자 테이블 / Admin user table
CREATE TABLE IF NOT EXISTS admin_user (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(255) UNIQUE NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,  -- BCrypt
    name                    VARCHAR(100) NOT NULL,
    tenant_type             VARCHAR(20) NOT NULL,   -- OPERATOR, HEADQUARTERS, MERCHANT
    tenant_id               UUID,                   -- headquarters.id or merchant.id (OPERATOR is null)
    role                    VARCHAR(20) NOT NULL,   -- ADMIN, MANAGER, VIEWER
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at           TIMESTAMP,
    failed_login_attempts   INTEGER NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Refresh Token 테이블 / Refresh token table
CREATE TABLE IF NOT EXISTS refresh_token (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id       UUID NOT NULL REFERENCES admin_user(id),
    token_hash          VARCHAR(255) NOT NULL,  -- SHA-256 hash
    expires_at          TIMESTAMP NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at          TIMESTAMP,              -- Revocation timestamp
    device_info         VARCHAR(255)            -- Optional: device info
);

-- API Key 테이블 / API key table
-- NOTE: plaintext API key is never stored. Only SHA-256 hash is stored.
CREATE TABLE IF NOT EXISTS api_key (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_type     VARCHAR(20) NOT NULL,        -- OPERATOR, HEADQUARTERS, MERCHANT
    tenant_id       UUID,                        -- headquarters.id or merchant.id (OPERATOR is null)
    name            VARCHAR(100) NOT NULL,
    key_hash        VARCHAR(64) UNIQUE NOT NULL, -- SHA-256 hex
    key_prefix      VARCHAR(16) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP
);

-- ============================================
-- 인덱스 / Indexes
-- ============================================

-- 비즈니스 도메인 인덱스 / Business domain indexes
CREATE INDEX IF NOT EXISTS idx_merchant_headquarters ON merchant(headquarters_id);
CREATE INDEX IF NOT EXISTS idx_payment_merchant ON payment_transaction(merchant_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payment_transaction(status);
CREATE INDEX IF NOT EXISTS idx_payment_requested_at ON payment_transaction(requested_at);
CREATE INDEX IF NOT EXISTS idx_refund_payment ON refund_transaction(payment_id);

-- 인증 관련 인덱스 / Authentication indexes
CREATE INDEX IF NOT EXISTS idx_admin_user_email ON admin_user(email);
CREATE INDEX IF NOT EXISTS idx_admin_user_tenant ON admin_user(tenant_type, tenant_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_token(token_hash);

CREATE INDEX IF NOT EXISTS idx_api_key_tenant ON api_key(tenant_type, tenant_id);

-- ============================================
-- 초기 데이터 (개발용) / Initial data (for development)
-- ============================================

-- 운영사 관리자 계정 (비밀번호: admin123!)
-- Operator admin account (password: admin123!)
-- BCrypt hash of 'admin123!'
INSERT INTO admin_user (id, email, password_hash, name, tenant_type, role, status)
VALUES (
    gen_random_uuid(),
    'admin@pgdemo.com',
    '$2a$10$JR2W3Nx0p1kHMoRAXEF6ZObGjiCVz9Tz4F0doOqLJwuA/pSNtbJ3q',
    '시스템 관리자',
    'OPERATOR',
    'ADMIN',
    'ACTIVE'
) ON CONFLICT (email) DO NOTHING;
