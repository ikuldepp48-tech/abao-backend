-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_011__auth_user_lock_until.sql
-- Geihou is the platform; Abao is tenant sample #1 only.

ALTER TABLE auth_user
  ADD COLUMN lock_until DATETIME NULL COMMENT '登录锁定到期时间(NULL=未锁定)' AFTER login_fail_count;
