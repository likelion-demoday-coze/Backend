-- 실행 전 컬럼 존재 여부 확인
SELECT COLUMN_NAME,
       COLUMN_TYPE,
       IS_NULLABLE,
       COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'member'
  AND COLUMN_NAME = 'time_attack_unlimited';

-- 위 조회 결과가 없을 때만 실행
ALTER TABLE member
    ADD COLUMN time_attack_unlimited BOOLEAN NOT NULL DEFAULT FALSE;

-- 컬럼 적용 확인
SELECT COLUMN_NAME,
       COLUMN_TYPE,
       IS_NULLABLE,
       COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'member'
  AND COLUMN_NAME = 'time_attack_unlimited';