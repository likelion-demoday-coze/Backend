-- [필수 배포 절차]
-- 1. 애플리케이션 배포 전에 운영 DB에서 아래 사전 확인 쿼리를 실행한다.
--
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'member'
--   AND COLUMN_NAME IN (
--                       'character_stage',
--                       'confirmed_character_stage',
--                       'pending_evolution_stage'
--     );
--
-- 2. 조회 결과가 0건일 때만 아래 컬럼 추가 ALTER TABLE을 실행한다.
--    세 컬럼이 모두 존재한다면 컬럼 추가는 건너뛰고 백필부터 다시 실행한다.
--    일부 컬럼만 존재한다면 전체 ALTER TABLE을 재실행하지 말고,
--    존재하지 않는 컬럼의 ADD COLUMN 절만 별도로 실행한 뒤 백필부터 계속한다.
--    MySQL의 ALTER TABLE은 암묵적으로 커밋되므로 중간 실패 후 이 확인 없이
--    전체 스크립트를 다시 실행하면 중복 컬럼 오류가 발생할 수 있다.

ALTER TABLE member
    ADD COLUMN character_stage VARCHAR(20) NULL
        AFTER current_stock,
    ADD COLUMN confirmed_character_stage VARCHAR(20) NULL
        AFTER character_stage,
    ADD COLUMN pending_evolution_stage VARCHAR(20) NULL
        AFTER confirmed_character_stage;

ALTER TABLE member
    ALTER COLUMN character_stage SET DEFAULT 'BASIC',
    ALTER COLUMN confirmed_character_stage SET DEFAULT 'BASIC';

-- 3. 기존 회원의 현재 주가를 기준으로 단계를 백필한다.
--    WHERE character_stage IS NULL 조건이 있어 재실행해도 기존 값을 덮어쓰지 않는다.
UPDATE member
SET character_stage =
        CASE
            WHEN current_stock < 100 THEN 'BABY'
            WHEN current_stock < 250 THEN 'BASIC'
            WHEN current_stock < 1500 THEN 'BIG_HAND'
            ELSE 'ROYAL'
            END
WHERE character_stage IS NULL;

-- 마지막으로 확인한 단계를 백필한다.
-- 미확인 진화가 있으면 그 직전 단계를, 없으면 현재 단계를 확정 단계처럼 취급한다.
UPDATE member
SET confirmed_character_stage =
        CASE
            WHEN pending_evolution_stage = 'ROYAL' THEN 'BIG_HAND'
            WHEN pending_evolution_stage = 'BIG_HAND' THEN 'BASIC'
            WHEN pending_evolution_stage = 'BASIC' THEN 'BABY'
            ELSE character_stage
            END
WHERE confirmed_character_stage IS NULL;

-- 4. 백필 결과에 NULL이 없는지 확인한 뒤 NOT NULL 제약을 적용한다.
--
-- SELECT COUNT(*) AS null_stage_count
-- FROM member
-- WHERE character_stage IS NULL
--    OR confirmed_character_stage IS NULL;
--
-- 위 결과가 0이어야 한다.
ALTER TABLE member
    MODIFY COLUMN character_stage VARCHAR(20)
        NOT NULL DEFAULT 'BASIC',
    MODIFY COLUMN confirmed_character_stage VARCHAR(20)
        NOT NULL DEFAULT 'BASIC';

-- 5. 적용 결과를 검증한 뒤 JPA_DDL_AUTO=validate 애플리케이션을 배포한다.
SELECT COUNT(*) AS null_stage_count
FROM member
WHERE character_stage IS NULL
   OR confirmed_character_stage IS NULL;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'member'
  AND COLUMN_NAME IN (
                      'character_stage',
                      'pending_evolution_stage',
                      'confirmed_character_stage'
    );
