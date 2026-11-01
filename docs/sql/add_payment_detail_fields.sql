-- 결제 상세 화면에 필요한 페이앱 승인·환불 정보를 저장한다.
-- 운영 DB의 실제 컬럼 존재 여부를 확인한 뒤 한 번만 실행한다.
ALTER TABLE payment
    ADD COLUMN card_name VARCHAR(50) NULL,
    ADD COLUMN receipt_url VARCHAR(500) NULL,
    ADD COLUMN cancellation_requested_at DATETIME(6) NULL;

-- 적용 확인
SHOW COLUMNS FROM payment
    WHERE Field IN (
        'card_name',
        'receipt_url',
        'cancellation_requested_at'
    );
