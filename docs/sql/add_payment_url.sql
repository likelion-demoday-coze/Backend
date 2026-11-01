-- 결제창 이탈 후 유효한 미결제 주문의 결제창으로 재진입하기 위한 URL 저장
--
-- [필수 배포 절차]
-- 1. 애플리케이션 배포 전에 운영 DB에서 아래 사전 확인 쿼리를 실행한다.
--    조회 결과가 0건일 때만 ALTER TABLE을 실행한다.
--
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'payment'
--   AND COLUMN_NAME = 'payment_url';
--
-- 2. 아래 ALTER TABLE을 실행한다.
ALTER TABLE payment
    ADD COLUMN payment_url VARCHAR(1000) NULL
        AFTER provider_transaction_id;

-- 3. 아래 검증 쿼리를 실행하고 다음 조건을 확인한다.
--    - 조회 결과: 1건
--    - COLUMN_TYPE: varchar(1000)
--    - IS_NULLABLE: YES
--
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'payment'
--   AND COLUMN_NAME = 'payment_url';
--
-- 4. 검증이 완료된 뒤 JPA_DDL_AUTO=validate 설정의 애플리케이션을 배포한다.
--    이미 컬럼이 존재하면 ALTER TABLE을 다시 실행하지 않는다.
