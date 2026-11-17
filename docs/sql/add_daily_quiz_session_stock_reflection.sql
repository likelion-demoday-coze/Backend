-- 실행 전 컬럼 존재 여부 확인
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'daily_quiz_session'
  AND COLUMN_NAME = 'stock_reflection_applied';

-- 조회 결과가 없을 때만 실행한다.
-- 기존 세션은 당시 반영 여부를 정확히 복원할 수 없으므로 FALSE로 이관한다.
ALTER TABLE daily_quiz_session
    ADD COLUMN stock_reflection_applied BOOLEAN NOT NULL DEFAULT FALSE;

-- 적용 결과 확인
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'daily_quiz_session'
  AND COLUMN_NAME = 'stock_reflection_applied';
