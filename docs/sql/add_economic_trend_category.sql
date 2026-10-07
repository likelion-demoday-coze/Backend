-- 기존 DB용 수동 변경 절차. 배포 전 백업하고 economic_trend.category가 아직 없을 때 실행한다.
-- 현재 프로젝트에는 Flyway가 없으므로 이 파일은 자동 실행되지 않는다.
-- 서버의 ddl-auto=update가 이미 컬럼을 추가했다면 ADD COLUMN은 건너뛰고 UPDATE만 실행한다.
ALTER TABLE economic_trend ADD COLUMN category VARCHAR(40) NULL;

-- 과거 AI 콘텐츠를 임의로 재분류하지 않고 미분류 기록을 기타로 보완한다.
UPDATE economic_trend SET category = 'OTHER' WHERE category IS NULL;
