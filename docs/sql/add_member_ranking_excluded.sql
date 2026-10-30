-- [필수 배포 절차]
-- 1. 애플리케이션 배포 전에 아래 ALTER TABLE을 실행한다.
-- 2. 운영 DB에서 랭킹 제외 대상인 팀원/운영 계정의 member_id를 확인한다.
-- 3. 아래 UPDATE문의 자리표시자를 실제 member_id로 교체한 뒤 실행한다.
-- 4. 검증 쿼리 결과에서 대상 계정이 모두 ranking_excluded = TRUE인지 확인한다.
-- 5. 대상 누락 여부를 확인하기 전에는 애플리케이션을 배포하지 않는다.

ALTER TABLE member
    ADD COLUMN ranking_excluded BOOLEAN NOT NULL DEFAULT FALSE;

-- [필수] 운영 계정 제외 처리
-- 아래 자리표시자를 실제 팀원/운영 계정의 member_id로 교체한다.
-- UPDATE member
-- SET ranking_excluded = TRUE
-- WHERE member_id IN (<TEAM_MEMBER_ID_1>, <TEAM_MEMBER_ID_2>);

-- [필수] 적용 결과 검증
-- UPDATE에 사용한 member_id 목록과 동일한 목록으로 조회한다.
-- 모든 대상 행의 ranking_excluded 값이 1(TRUE)이어야 한다.
-- SELECT member_id, nickname, status, ranking_excluded
-- FROM member
-- WHERE member_id IN (<TEAM_MEMBER_ID_1>, <TEAM_MEMBER_ID_2>)
-- ORDER BY member_id;

-- 전체 제외 계정 수가 운영팀이 정한 예상 수와 일치하는지도 확인한다.
-- SELECT COUNT(*) AS ranking_excluded_member_count
-- FROM member
-- WHERE ranking_excluded = TRUE;
