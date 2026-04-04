-- V5: Fix architecture violations
-- Mục tiêu: alert-module không được phụ thuộc vào auth-module
--
-- Thay đổi:
--   alerts.alert_events.acknowledged_by: UUID (FK → users) → VARCHAR(100) (username string)
--   Lý do: AlertService chỉ cần lưu "ai acknowledge", không cần FK vào bảng users.
--          Dùng username string để alert-module độc lập với auth-module.

ALTER TABLE alerts.alert_events
    ALTER COLUMN acknowledged_by TYPE VARCHAR(100) USING NULL;
