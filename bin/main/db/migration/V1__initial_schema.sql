-- Schema
-- CREATE SCHEMA IF NOT EXISTS maintenance_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- USE maintenance_app;

-- 1) 기존 DB 삭제
DROP DATABASE IF EXISTS maintenance_app;

-- 2) 새로 생성
CREATE DATABASE maintenance_app
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- 3) 사용 DB 선택 (선택사항)
USE maintenance_app;

CREATE TABLE flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success TINYINT(1) NOT NULL,
    PRIMARY KEY (installed_rank),
    KEY flyway_schema_history_s_idx (success)
);


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
  role      ENUM('BRANCH','HQ','VENDOR') NOT NULL DEFAULT 'BRANCH',
  is_active  BOOLEAN NOT NULL DEFAULT TRUE,       -- 활성/비활성
  branch_id  BIGINT UNSIGNED NULL,                -- BRANCH일 경우 지점 FK
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role (role),
  KEY idx_user_branch (branch_id),
  CONSTRAINT fk_user_branch
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB;


-- 3) maintenance_request
CREATE TABLE maintenance_request (
  request_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  branch_id      BIGINT UNSIGNED NOT NULL,
  requester_id   BIGINT UNSIGNED NOT NULL,
  title          VARCHAR(200)    NOT NULL,
  description    TEXT,

  status         ENUM(
    'DRAFT',
    'REQUESTED',          -- 지점이 요청 제출
    'ESTIMATING',         -- HQ가 vendor에게 견적 요청
    'APPROVAL_PENDING',   -- vendor가 견적 제출 → HQ 승인 대기
    'IN_PROGRESS',        -- 승인 후 공사 진행 중
    'COMPLETED',          -- 공사 완료
    'REJECTED'            -- HQ가 반려
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

  -- 견적 / 일정 / 사유
  estimate_amount      DECIMAL(15,2) NULL,
  estimate_comment     TEXT         NULL,
  work_start_date      DATE         NULL,
  work_end_date        DATE         NULL,
  rejected_reason      VARCHAR(500) NULL,

  result_comment     TEXT NULL,
  result_photo_url   VARCHAR(2048) NULL,
  work_completed_at  DATETIME NULL,

  -- 타임스탬프
  submitted_at         DATETIME NULL, -- 지점이 정식 제출한 시점
  vendor_submitted_at  DATETIME NULL, -- 벤더가 견적 제출한 시점
  approved_by          BIGINT UNSIGNED NULL,
  approved_at          DATETIME NULL,

  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (request_id),

  KEY idx_mr_branch (branch_id),
  KEY idx_mr_status (status),
  KEY idx_mr_category (category),
  KEY idx_mr_vendor (vendor_id),
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

  CONSTRAINT fk_mr_approved_by
    FOREIGN KEY (approved_by) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB;



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
create table maintenance_photo (
  id bigint unsigned auto_increment primary key,
  maintenance_id bigint unsigned not null,
  file_key varchar(255) not null,
  url varchar(500) not null,
  constraint fk_maintenance_photo_maintenance
    foreign key (maintenance_id)
    references maintenance_request (request_id)
) engine=InnoDB;

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

-- 8) notification
CREATE TABLE notification (
  notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  title           VARCHAR(200)    NOT NULL,
  message         TEXT,
  type            ENUM('REQUEST','APPROVAL','REJECTION','COMPLETION') NOT NULL,
  is_read         TINYINT(1) NOT NULL DEFAULT 0,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id),
  KEY idx_n_user (user_id),
  KEY idx_n_isread (is_read),
  CONSTRAINT fk_n_user
    FOREIGN KEY (user_id) REFERENCES user_tb(user_id)
    ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB;

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
