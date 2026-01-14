-- Schema
-- CREATE SCHEMA IF NOT EXISTS maintenance_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- USE maintenance_app;

-- 1) 기존 DB 삭제
-- DROP DATABASE IF EXISTS maintenance_app;         

-- 2) 새로 생성
-- CREATE DATABASE maintenance_app
--   DEFAULT CHARACTER SET utf8mb4
--   COLLATE utf8mb4_0900_ai_ci;

-- 3) 사용 DB 선택 (선택사항)
-- USE maintenance_app;



-- 1) branch
CREATE TABLE branch (
  branch_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  branch_name   VARCHAR(100)    NOT NULL,         -- 지점명
  phone         VARCHAR(20),                      -- 지점 대표번호
  address_name  VARCHAR(200),                     -- 지점 주소
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (branch_id),
  UNIQUE KEY uk_branch_name (branch_name)         -- 지점명 유니크
) ENGINE=InnoDB;

-- 2) user
CREATE TABLE user_tb (
  user_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username   VARCHAR(50)  NOT NULL,               -- 로그인 아이디
  password   VARCHAR(255) NOT NULL,               -- 비밀번호(해시)
  name       VARCHAR(100) NULL,                   -- 사용자 이름
  phone      VARCHAR(20)  NULL,                   -- 사용자 개인 휴대폰 번호
  address    VARCHAR(255) NULL,
  role       ENUM('BRANCH','HQ','VENDOR') NOT NULL DEFAULT 'BRANCH',

  is_active  TINYINT(1) NOT NULL DEFAULT 1,       -- 활성/비활성

  -- ✅ 초기 비밀번호(1234)로 생성/초기화된 계정이면 true
  must_change_password TINYINT(1) NOT NULL DEFAULT 1,

  -- ✅ 로그인 5회 실패 영구잠금용
  login_fail_count INT NOT NULL DEFAULT 0,        -- 로그인 실패 횟수
  account_locked   TINYINT(1) NOT NULL DEFAULT 0, -- 영구 잠금 여부(1이면 로그인 차단)

  branch_id  BIGINT UNSIGNED NULL,                -- BRANCH일 경우 지점 FK
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role (role),
  KEY idx_user_branch (branch_id),
  KEY idx_user_must_change_password (must_change_password),

  -- (선택) HQ에서 잠금 계정만 빠르게 필터링할 때 유용
  KEY idx_user_account_locked (account_locked),

  CONSTRAINT fk_user_branch
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE vendor_worker (
  worker_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  vendor_id   BIGINT UNSIGNED NOT NULL,     -- user_tb.user_id (벤더 계정)

  team_label  VARCHAR(50)  NULL,            -- 예: '1팀'
  name        VARCHAR(100) NOT NULL,        -- 예: '홍길동'
  phone       VARCHAR(30)  NULL,

  is_active   TINYINT(1) NOT NULL DEFAULT 1,

  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (worker_id),
  KEY idx_vw_vendor (vendor_id),
  KEY idx_vw_active (is_active),

  CONSTRAINT fk_vw_vendor
    FOREIGN KEY (vendor_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 
CREATE TABLE maintenance_request (
  request_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  branch_id      BIGINT UNSIGNED NOT NULL,
  requester_id   BIGINT UNSIGNED NOT NULL,

  title          VARCHAR(200)    NOT NULL,
  description    TEXT,

  status         ENUM(
    'DRAFT',
    'REQUESTED',
    'HQ1_REJECTED',
    'ESTIMATING',
    'APPROVAL_PENDING',
    'HQ2_REJECTED',
    'IN_PROGRESS',
    'COMPLETED'
  ) NOT NULL DEFAULT 'DRAFT',

  category       ENUM(
    'ELECTRICAL_COMMUNICATION',
    'LIGHTING',
    'HVAC',
    'WATER_SUPPLY_DRAINAGE',
    'SAFETY_HYGIENE',
    'ETC'
  ) NOT NULL DEFAULT 'ETC',

  vendor_id      BIGINT UNSIGNED NULL,
  vendor_worker_id BIGINT UNSIGNED NULL,   -- ✅ 추가: 벤더 작업자(팀/담당) 선택값

  -- (레거시/참조용) 현재는 attempt 테이블이 정본
  estimate_amount         DECIMAL(15,2) NULL,
  estimate_comment        TEXT         NULL,
  work_start_date         DATE         NULL,
  work_end_date           DATE         NULL,
  estimate_resubmit_count INT NOT NULL DEFAULT 0,

  request_approved_comment VARCHAR(500) NULL,

  request_rejected_reason  VARCHAR(500) NULL,
  estimate_rejected_reason VARCHAR(500) NULL,

  result_comment     TEXT NULL,
  result_photo_url   VARCHAR(2048) NULL,
  work_completed_at  DATETIME NULL,

  submitted_at         DATETIME NULL,
  vendor_submitted_at  DATETIME NULL,

  request_approved_by BIGINT UNSIGNED NULL,
  request_approved_at DATETIME NULL,

  estimate_approved_by BIGINT UNSIGNED NULL,
  estimate_approved_at DATETIME NULL,

  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (request_id),

  KEY idx_mr_branch (branch_id),
  KEY idx_mr_status (status),
  KEY idx_mr_category (category),
  KEY idx_mr_vendor (vendor_id),
  KEY idx_mr_vendor_worker (vendor_worker_id),  -- ✅ 추가
  KEY idx_mr_requester (requester_id),
  KEY idx_mr_created (created_at),

  CONSTRAINT fk_mr_branch
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,

  CONSTRAINT fk_mr_requester
    FOREIGN KEY (requester_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,

  CONSTRAINT fk_mr_vendor
    FOREIGN KEY (vendor_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE SET NULL,

  CONSTRAINT fk_mr_vendor_worker              -- ✅ 추가
    FOREIGN KEY (vendor_worker_id) REFERENCES vendor_worker(worker_id)
    ON UPDATE RESTRICT ON DELETE SET NULL,

  CONSTRAINT fk_mr_request_approved_by
    FOREIGN KEY (request_approved_by) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE SET NULL,

  CONSTRAINT fk_mr_estimate_approved_by
    FOREIGN KEY (estimate_approved_by) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



-- 4) estimate
CREATE TABLE estimate (
  estimate_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id    BIGINT UNSIGNED NOT NULL,
  vendor_id     BIGINT UNSIGNED NOT NULL,
  amount        DECIMAL(15,2)   NOT NULL,
  estimate_file VARCHAR(255),
  status        ENUM('SUBMITTED','APPROVED','REJECTED') NOT NULL DEFAULT 'SUBMITTED',
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (estimate_id),
  KEY idx_est_req (request_id),
  KEY idx_est_vendor (vendor_id),
  KEY idx_est_status (status),
  CONSTRAINT fk_est_req
    FOREIGN KEY (request_id) REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT fk_est_vendor
    FOREIGN KEY (vendor_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE maintenance_estimate_attempt (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  maintenance_id BIGINT UNSIGNED NOT NULL,
  attempt_no INT NOT NULL, -- 1 or 2

  estimate_amount VARCHAR(50) NOT NULL,
  estimate_comment TEXT NULL,
  work_start_date DATE NULL,
  work_end_date DATE NULL,

  vendor_submitted_at DATETIME NOT NULL,

  -- ✅ 작업자 스냅샷(선택)
  worker_id BIGINT UNSIGNED NULL,
  worker_team_label VARCHAR(50) NULL,
  worker_name VARCHAR(100) NULL,
  worker_phone VARCHAR(50) NULL,

  hq_decision VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
  hq_decided_at DATETIME NULL,
  hq_decided_by_name VARCHAR(100) NULL,
  hq_reject_reason TEXT NULL,

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),

  CONSTRAINT uk_maintenance_attempt UNIQUE (maintenance_id, attempt_no),

  CONSTRAINT fk_mea_maintenance
    FOREIGN KEY (maintenance_id)
    REFERENCES maintenance_request (request_id)
    ON UPDATE RESTRICT
    ON DELETE CASCADE,

  INDEX idx_mea_mid (maintenance_id),
  INDEX idx_mea_mid_attempt (maintenance_id, attempt_no),
  INDEX idx_mea_hq_decision (hq_decision),

  -- ✅ 작업자 조회/필터 대비(선택)
  INDEX idx_mea_worker_id (worker_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5) work_order
CREATE TABLE work_order (
  work_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id  BIGINT UNSIGNED NOT NULL,
  vendor_id   BIGINT UNSIGNED NOT NULL,
  start_date  DATE,
  end_date    DATE,
  status      ENUM('IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS',
  report      TEXT,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (work_id),
  KEY idx_wo_req (request_id),
  KEY idx_wo_vendor (vendor_id),
  KEY idx_wo_status (status),
  CONSTRAINT fk_wo_req
    FOREIGN KEY (request_id) REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT fk_wo_vendor
    FOREIGN KEY (vendor_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 6) Photo attachment
CREATE TABLE maintenance_photo (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  maintenance_id BIGINT UNSIGNED NOT NULL,

  file_key VARCHAR(255) NOT NULL,
  public_url VARCHAR(1000) NOT NULL,

  photo_type VARCHAR(20) NOT NULL COMMENT 'REQUEST/ESTIMATE/RESULT',
  attempt_no INT NULL COMMENT 'ESTIMATE only: 1 or 2, otherwise NULL',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),

  CONSTRAINT fk_mp_maintenance
    FOREIGN KEY (maintenance_id)
    REFERENCES maintenance_request (request_id)
    ON UPDATE RESTRICT
    ON DELETE CASCADE,

  INDEX idx_mp_mid (maintenance_id),
  INDEX idx_mp_mid_type (maintenance_id, photo_type),
  INDEX idx_mp_mid_type_attempt (maintenance_id, photo_type, attempt_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 7) comment
CREATE TABLE comment (
  comment_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id  BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  content     TEXT            NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (comment_id),
  KEY idx_c_req (request_id),
  KEY idx_c_user (user_id),
  CONSTRAINT fk_c_req
    FOREIGN KEY (request_id) REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT fk_c_user
    FOREIGN KEY (user_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 8) notification (엔티티 기준 최종본)
CREATE TABLE notification (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,

  user_id BIGINT UNSIGNED NOT NULL,
  maintenance_id BIGINT UNSIGNED NOT NULL,

  status VARCHAR(50) NOT NULL,
  event_type VARCHAR(30) NOT NULL,

  -- ✅ dedupe용 키
  attempt_no INT NOT NULL,

  message VARCHAR(255) NOT NULL,

  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  KEY idx_notif_user_created (user_id, created_at),
  KEY idx_notif_user_read (user_id, is_read),

  -- ✅ 중복 방지 (상태변경은 attempt_no=0 고정이라 status별 1번만 쌓임)
  -- ✅ 견적수정은 attempt_no를 매번 다르게 넣으면 매번 신규 생성됨
  UNIQUE KEY uq_notif_dedupe (
    user_id,
    maintenance_id,
    event_type,
    status,
    attempt_no
  ),

  CONSTRAINT fk_notif_user
    FOREIGN KEY (user_id)
    REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT
    ON DELETE CASCADE,

  CONSTRAINT fk_notif_maintenance
    FOREIGN KEY (maintenance_id)
    REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT
    ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


-- 9) activity_log
CREATE TABLE activity_log (
  log_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  request_id BIGINT UNSIGNED NOT NULL,
  action     VARCHAR(100)    NOT NULL,
  details    TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_al_user (user_id),
  KEY idx_al_req (request_id),
  KEY idx_al_created (created_at),
  CONSTRAINT fk_al_user
    FOREIGN KEY (user_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_al_req
    FOREIGN KEY (request_id) REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB;

