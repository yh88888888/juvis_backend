-- Schema
--CREATE SCHEMA IF NOT EXISTS maintenance_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--USE maintenance_app;

-- 1) branch
CREATE TABLE branch (
  branch_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  branch_name VARCHAR(100)    NOT NULL,
  manager_name VARCHAR(100),
  phone       VARCHAR(20),
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (branch_id),
  UNIQUE KEY uk_branch_name (branch_name)
) ENGINE=InnoDB;

-- 2) user
CREATE TABLE user_account (
  user_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)     NOT NULL,
  password    VARCHAR(255)    NOT NULL,
  name        VARCHAR(100)    NOT NULL,
  email       VARCHAR(100),
  phone       VARCHAR(20),
  role        ENUM('BRANCH','HQ','VENDOR') NOT NULL,
  branch_id   BIGINT UNSIGNED NULL,
  is_active   TINYINT(1) NOT NULL DEFAULT 1,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
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
  urgency        ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
  status         ENUM('REQUESTED','ESTIMATING','APPROVAL_PENDING','IN_PROGRESS','COMPLETED','REJECTED') NOT NULL DEFAULT 'REQUESTED',
  vendor_id      BIGINT UNSIGNED NULL,
  approved_by    BIGINT UNSIGNED NULL,
  approved_at    DATETIME NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (request_id),
  KEY idx_mr_branch (branch_id),
  KEY idx_mr_status (status),
  KEY idx_mr_vendor (vendor_id),
  KEY idx_mr_requester (requester_id),
  KEY idx_mr_created (created_at),
  CONSTRAINT fk_mr_branch
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_mr_requester
    FOREIGN KEY (requester_id) REFERENCES user_account(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_mr_vendor
    FOREIGN KEY (vendor_id) REFERENCES user_account(user_id)
    ON UPDATE RESTRICT ON DELETE SET NULL,
  CONSTRAINT fk_mr_approved_by
    FOREIGN KEY (approved_by) REFERENCES user_account(user_id)
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
    FOREIGN KEY (vendor_id) REFERENCES user_account(user_id)
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
    FOREIGN KEY (vendor_id) REFERENCES user_account(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 6) file_attachment
CREATE TABLE file_attachment (
  file_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id   BIGINT UNSIGNED NOT NULL,
  work_id      BIGINT UNSIGNED NULL,
  file_path    VARCHAR(255)    NOT NULL,
  file_type    ENUM('PHOTO','ESTIMATE','REPORT') NOT NULL,
  uploaded_by  BIGINT UNSIGNED NOT NULL,
  uploaded_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (file_id),
  KEY idx_fa_req (request_id),
  KEY idx_fa_work (work_id),
  KEY idx_fa_uploader (uploaded_by),
  CONSTRAINT fk_fa_req
    FOREIGN KEY (request_id) REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT fk_fa_work
    FOREIGN KEY (work_id) REFERENCES work_order(work_id)
    ON UPDATE RESTRICT ON DELETE SET NULL,
  CONSTRAINT fk_fa_uploader
    FOREIGN KEY (uploaded_by) REFERENCES user_account(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

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
    FOREIGN KEY (user_id) REFERENCES user_account(user_id)
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
    FOREIGN KEY (user_id) REFERENCES user_account(user_id)
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
    FOREIGN KEY (user_id) REFERENCES user_account(user_id)
    ON UPDATE RESTRICT ON DELETE SET NULL,   -- (원한다면 CASCADE/RESTRICT로 조정)
  CONSTRAINT fk_al_req
    FOREIGN KEY (request_id) REFERENCES maintenance_request(request_id)
    ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB;
