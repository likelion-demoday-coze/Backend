-- 애플리케이션 배포 전에 운영 DB에 먼저 적용한다.
ALTER TABLE member
    ADD COLUMN ranking_excluded BOOLEAN NOT NULL DEFAULT FALSE;

-- 팀원 계정의 실제 member_id를 확인한 뒤 운영 DB에서 별도로 실행한다.
-- UPDATE member
-- SET ranking_excluded = TRUE
-- WHERE member_id IN (...);
