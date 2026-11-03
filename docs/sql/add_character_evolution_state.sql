-- [필수 배포 절차]
-- 애플리케이션 배포 전에 운영 DB에 적용한다.

ALTER TABLE member
    ADD COLUMN character_stage VARCHAR(20) NULL
        AFTER current_stock,
    ADD COLUMN pending_evolution_stage VARCHAR(20) NULL
        AFTER character_stage;

-- 기존 회원의 현재 주가를 기준으로 단계 백필
UPDATE member
SET character_stage =
        CASE
            WHEN current_stock < 100 THEN 'BABY'
            WHEN current_stock < 250 THEN 'BASIC'
            WHEN current_stock < 1500 THEN 'BIG_HAND'
            ELSE 'ROYAL'
            END
WHERE character_stage IS NULL;

ALTER TABLE member
    MODIFY COLUMN character_stage VARCHAR(20) NOT NULL;

-- 적용 결과 검증
SELECT
    character_stage,
    COUNT(*) AS member_count
FROM member
GROUP BY character_stage
ORDER BY character_stage;

SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'member'
  AND COLUMN_NAME IN (
                      'character_stage',
                      'pending_evolution_stage'
    );