-- 결제창 이탈 후 유효한 미결제 주문의 결제창으로 재진입하기 위한 URL 저장
ALTER TABLE payment
    ADD COLUMN payment_url VARCHAR(1000) NULL
        AFTER provider_transaction_id;
