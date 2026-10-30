# EC2 + Nginx Blue/Green 운영

현재 CD는 `deploy.sh`를 통해 `deploy-blue-green.sh`를 실행한다. 사용자 → EC2 Nginx HTTPS → Blue(127.0.0.1:8080) 또는 Green(127.0.0.1:8081) → 동일 MySQL 순서다. 앱 컨테이너 내부 포트는 둘 다 8080이다. 관리 포트 9090은 컨테이너 loopback에서만 사용한다.

## 전환과 실패 처리

1. KST 23:50–00:20에는 새 배포를 거절한다. 서버 flock과 GitHub concurrency로 배포/롤백을 직렬화한다.
2. 현재 Nginx upstream과 실제 응답을 대조한다. 기존 버전이 Blue/Green 프로토콜을 지원하는지 확인한다.
3. 서버·DB 용량 점검, SHA 이미지 pull, DB 백업을 수행한다. 이 단계에서는 현재 앱을 건드리지 않는다.
4. 비활성 슬롯의 이전 컨테이너만 정리하고 새 SHA 이미지를 시작한다. 기존 슬롯은 계속 서비스한다.
5. Docker health check가 `/actuator/health/readiness` 성공을 확인한다. readiness에는 DB 연결도 포함한다. 동일 이미지는 주기적으로 이 readiness를 검사한다.
6. 새 슬롯의 loopback 주소로 인증·홈·퀴즈·상품 조회 smoke를 수행한다. 전용 회원의 Secure 쿠키를 원래 HTTPS 도메인 기준으로 선택해, 접근이 제한된 임시 헤더 파일로 전달한다. 쿠키를 명령 인자나 CI 로그에 출력하지 않는다.
7. 보호 시간을 다시 확인한 후 upstream 파일을 같은 디렉터리에서 원자적으로 교체한다. `nginx -t`, graceful reload, 실제 로컬 라우팅 응답의 대상 포트 검증을 순서대로 수행한다.
8. 공개 HTTPS smoke, 필수 수집 검증 hook, 기본 60초 관찰을 수행한다. 실패하면 이전 upstream으로 reload하고 실제 라우팅 복구를 확인한다. 두 컨테이너는 조사와 진행 중 요청을 위해 유지한다. CD는 실패로 종료한다.
9. 성공한 슬롯/이미지를 기록하고, 전환 전에 존재하던 Nginx worker PID들이 모두 종료되기를 기다린다. 최대 180초 후에도 요청이 남아 있거나 자정 보호 시간에 들어갔다면 기존 앱을 유지한다. 그렇지 않으면 기존 앱에 SIGTERM을 보낸다.

Spring은 HTTP 요청에 최대 30초, 실행 중 배치에 최대 30분의 종료 대기를 제공한다. Docker는 최대 1900초 후 강제 종료하므로 무한 대기는 아니다. 요청·배치 소요 시간과 이 제한을 모니터링해야 한다. 배치가 계속 실행되면 ShedLock의 별도 heartbeat가 lease를 연장한다. `SOAK_SECONDS`, `DRAIN_SECONDS`는 deploy.env에서 변경할 수 있으나 0은 모의 테스트에만 사용한다.

Nginx의 graceful reload 동작은 [공식 문서](https://nginx.org/en/docs/control.html), 앱 종료는 [Spring Boot 문서](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)를 참고한다. 단일 EC2의 호스트 장애까지 방어하는 HA 구성은 아니다.

## 최초 전환: 두 버전 실행 전 반드시 완료

기존 이미지에는 ShedLock이 없으므로 **기존 버전과 이 변경 버전을 곧바로 동시에 띄우면 안 된다.** 먼저 이 변경이 포함된 버전을 단일 인스턴스 기준 버전으로 설치한다. 첫 전환은 자정 보호 시간 밖의 계획된 작업으로 수행하고, 이후 패치부터 Blue/Green을 사용한다.

- EC2가 두 JVM과 MySQL 연결 풀을 감당하는지 확인한다. Docker, Bash, curl, Python 3, nginx, flock, timeout, MySQL 클라이언트, gzip이 필요하다.
- 백업 후 `docs/sql/add_shedlock.sql`을 DB에 적용한다. 앱 계정에는 lock 테이블 조회·삽입·갱신 권한이 필요하다.
- 이미 Spring Session JDBC 테이블이 있다면 그대로 사용한다. 없다면 **사용 중인 Spring Session 버전과 일치하는** `docs/sql/create_spring_session_mysql.sql`을 1회 적용한다. 이 파일은 현재 4.1.1 의존성의 공식 schema-mysql.sql이다. 기존 테이블에 다시 실행하지 않는다.
- 두 버전은 동일 DB, 세션 테이블, 쿠키 도메인/이름/보안 설정을 사용한다. 세션에 저장되는 인증 객체의 직렬화 호환성도 유지한다.
- Nginx 샘플의 `api.example.com`과 인증서 경로를 실제 값으로 바꾼다. HTTP 블록 안에 `nginx/backend.conf`를 설치하고 `/etc/nginx/backend/active-upstream.conf`에 Blue(8080)를 지정한다. SSL 인증서와 80/443 보안 그룹을 준비한다. 8080/8081/9090/18080은 외부에 개방하지 않는다.
- 기존 공개 8080 컨테이너를 종료하고, **이 변경이 포함된 SHA 이미지**로 `backend-blue`를 실행한다. 포트는 `127.0.0.1:8080:8080`, `.env`는 기존 파일, `JPA_DDL_AUTO=validate`, `SPRING_SESSION_JDBC_INITIALIZE_SCHEMA=never`, 관리 포트 9090/127.0.0.1을 사용한다. 이 최초 교체 중에는 짧은 점검 시간이 필요하다.
- Nginx를 검증/활성화하고 실제 HTTPS 로그인과 API, readiness, 수집 상태를 확인한다. 기준 이미지에 `com.coze.deployment.protocol=bluegreen-v1` 라벨이 있는지 확인한다.

검증을 마친 뒤에만 다음 상태를 초기화한다.

```bash
sudo install -d -m 700 /home/ubuntu/Backend/deploy-state
printf 'blue\n' | sudo tee /home/ubuntu/Backend/deploy-state/active-slot
sudo docker inspect --format '{{.Image}}' backend-blue | sudo tee /home/ubuntu/Backend/deploy-state/known-good-blue
printf 'v1\n' | sudo tee /home/ubuntu/Backend/deploy-state/blue-green-ready
```

marker는 최초 설정 완료에 대한 운영자의 확인이다. 스크립트가 Nginx나 DB 스키마를 임의로 설치하거나 운영 트래픽을 처음부터 재구성하지 않는다.

## 서버 설정

로컬 Docker Compose의 새 MySQL 볼륨에는 lock/세션 스키마를 init script로 생성한다. 기존 볼륨에는 init script가 다시 실행되지 않으므로 누락된 테이블을 별도로 적용해야 한다. 운영 데이터가 있는 볼륨을 삭제해서 초기화하지 않는다.

`/home/ubuntu/Backend/deploy.env`는 root 소유 0600의 신뢰된 shell 설정이다. 앱 `.env`를 source하지 않는다.

```bash
BACKUP_SCRIPT=/opt/backend-ops/backup-mysql.sh
PRE_DEPLOY_CHECK_SCRIPT=/opt/backend-ops/check-disk.sh
POST_DEPLOY_CHECK_SCRIPT=/opt/backend-ops/check-production-telemetry.sh
SMOKE_BASE_URL=https://api.example.com
SMOKE_COOKIE_FILE=/home/ubuntu/Backend/smoke.cookies
UPSTREAM_FILE=/etc/nginx/backend/active-upstream.conf
NGINX_PID_FILE=/run/nginx.pid
```

`smoke.cookies`는 유효한 테스트 회원의 Netscape 쿠키 파일(0600)이며 만료 전에 갱신한다. 자동 조회는 `/api/v1/members/me`, `/stocks/me`, `/daily-quizzes/today`, `/products`(각 경로의 `/api/v1` 접두사 포함)이다. HTTP 200과 JSON `isSuccess=true`를 모두 요구한다. 실제 OAuth 로그인, 퀴즈 제출, 결제 승인은 출시 담당자가 별도로 확인한다.

`POST_DEPLOY_CHECK_SCRIPT`는 환경별로 작성해야 하는 필수 검증이다. `DEPLOY_SHA`, `DEPLOY_RECORD_DIR`가 전달된다. 최신 요청의 로그·트레이스·메트릭 유입과 사전 승인된 시험 알림 수신을 검증한 경우에만 0을 반환한다. 최대 180초이며 Collector HTTP 200만으로 성공 처리하지 않는다. [관측 가이드](../../docs/observability.md)를 참고한다.

CD는 SHA별 `scripts/ops`를 서버 releases 디렉터리에 복사한다. 정기 작업에 사용하는 별도 `/opt/backend-ops`도 검토한 버전으로 설치하고 스크립트에 실행 권한을 부여한다. 서버 접속 정보는 기존 GitHub Secrets를 사용한다.

## 스케줄러, 세션, 결제, DB

랭킹 정산·주가 스냅샷·연속 학습 판정은 시작 이벤트와 자정 이벤트가 각각 같은 lock 이름을 사용한다. 트렌드 생성과 재시도도 같은 이름, 결제 만료/지급 재시도는 작업별 이름을 사용한다. 짧은 작업이 노드별로 연달아 실행되는 것을 줄이기 위해 최소 lease도 적용한다. DB 시간을 사용하고 heartbeat는 실행 스레드와 분리한다.

[ShedLock](https://github.com/lukas-krecan/shedlock)은 동일 작업의 동시 실행을 막고 다른 노드는 건너뛴다. 하루에 정확히 한 번 실행하거나 실패한 배치를 자동 재개하는 보장은 아니다. 날짜별 정산·보상 이력 및 기존 멱등성도 계속 필요하다. 프로세스가 죽으면 최대 30분의 lease가 남을 수 있으므로 실패 알림과 미처리 날짜 보완을 점검한다. lock 행을 임의로 삭제하지 않는다.

세션은 JDBC 저장소를 공유하며 새 서버가 기존 인증 세션을 읽는다. 결제 Feedback의 기존 DB 행 잠금과 결제별 이용권 유일 제약을 유지한다. 결제 POST를 Nginx가 자동 재시도하지 않도록 설정했다. 여러 서버에서 들어온 중복 콜백의 지급 1회 보장은 기존 동시성 테스트로 확인한다.

DB 변경은 `추가 → 호환 코드 배포 → 데이터 이관 → 구버전 종료/복구 기간 종료 → 제거` 순서로 적용한다. 두 버전 모두 같은 스키마를 읽을 수 있어야 한다. JPA validate만 수행하고 배포나 앱 롤백에서 DB를 자동 변경/복원하지 않는다.

## DB 백업·용량·복구 시험

`/home/ubuntu/Backend/backup.env`(0600):

```bash
MYSQL_DEFAULTS_FILE=/home/ubuntu/Backend/mysql-backup.cnf
MYSQL_DATABASE=demoday_db
BACKUP_DIR=/var/backups/backend
RESTORE_MYSQL_IMAGE=mysql:8.0 # 운영 버전/digest로 고정
```

`mysql-backup.cnf`(0600)는 `[client]` 아래 host, port, user, password와 필요한 TLS 검증 옵션을 둔다. 최소 권한 백업 계정을 사용한다. mysqldump는 InnoDB 기준 single-transaction이며 백업 도중 DDL을 실행하지 않는다. 백업 압축 검사와 SHA-256을 기록하고 실패하면 배포하지 않는다. 오프사이트 전송이 필요하면 BACKUP_SCRIPT wrapper가 전송까지 성공한 뒤 참조를 출력하게 한다.

`/home/ubuntu/Backend/disk.env`:

```bash
DB_DISK_PATH=/var/lib/docker/volumes/ACTUAL_VOLUME/_data
BACKUP_DIR=/var/backups/backend
MAX_DISK_PERCENT=85
```

실제 마운트를 확인하고 백업 디렉터리를 미리 생성한다. 루트·Docker·DB·백업 경로의 디스크와 inode를 점검한다. 최대 이미지/백업 크기를 감당할 절대 여유 공간도 확인한다. 원격 DB는 PRE_DEPLOY_CHECK_SCRIPT를 공급자 FreeStorageSpace 등 DB 측 지표와 서버 디스크를 함께 확인하는 hook으로 교체한다.

`systemd/`의 네 unit을 `/etc/systemd/system/`에 설치한다.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now backend-backup.timer backend-restore-test.timer
sudo systemctl start backend-backup.service
sudo systemctl start backend-restore-test.service
```

매일 03:00 KST(최대 5분 지연)에 백업하고 월요일 04:00 KST에 격리 MySQL import와 mysqlcheck를 수행한다. 복구 시험 DB는 공개 포트·운영 볼륨·외부 네트워크를 사용하지 않으며 이벤트 실행도 막는다. 실패 알림, 마지막 성공 시각, 암호화·오프사이트 저장·보존 정책은 별도 운영 설정이다. 매월 격리된 앱까지 연결해 로그인·데이터·결제 상태와 RPO/RTO를 검증한다.

## 수동 롤백과 기록

```bash
sudo bash /home/ubuntu/Backend/releases/DEPLOYED_SHA/scripts/ops/rollback.sh
```

반대 슬롯의 **known-good 이미지 ID**와 프로토콜 라벨을 대조하고, 필요하면 먼저 시작해 readiness와 직접 smoke를 확인한 뒤 Nginx를 전환한다. 현재 서버는 그동안 계속 요청을 받는다. 구버전이 살아 있다면 즉시 전환할 수 있고 이미 종료됐다면 시작 시간이 필요하다. 수동 롤백은 장애 대응이므로 자정에도 라우팅 복구를 허용하고 두 컨테이너를 모두 유지한다.

배포/롤백 결과는 `deploy-state/<시각>-<SHA>-<PID>/`에 기록한다. 다음 배포가 비활성 슬롯을 재사용하면 그 슬롯의 구버전 복구 창은 종료된다. 실패 후보에는 known-good 표시를 남기지 않아 수동 롤백 대상으로 오인하지 않게 한다. 이미지 SHA/ID와 백업 참조는 배포 기록에 남는다. 자동 image prune은 하지 않는다.

SIGKILL/호스트 장애에서는 trap이 실행되지 않을 수 있다. upstream 파일이 라우팅 기준이고 `active-slot`은 성공 기록이다. 실제 로컬 probe 응답, upstream 파일, 두 컨테이너 상태를 먼저 대조한다. Nginx 설정과 실제 응답이 다르면 자동 배포는 중단되므로 검증된 슬롯으로 upstream을 복원하고 `nginx -t`, reload, 공개 smoke를 수행한다. 앱 로그와 전체 inspect는 비밀값이 포함될 수 있으므로 제한된 서버에서만 확인한다.

## 적용 검증

CI의 Bash 테스트는 서비스 중인 슬롯을 검증 이전에 정지하지 않는지, Nginx 설정/전환 실패, 후보/공개 smoke 실패, 수집 실패, 자정 경계, drain timeout 및 수동 롤백을 모의 검증한다. Java 테스트는 독립된 두 Spring 컨텍스트의 공유 DB lock과 두 JDBC 세션 저장소의 인증 공유, 기존 결제 동시성을 확인한다.

실제 EC2/Nginx 설치·인증서·MySQL 잠금·알림 전송·실제 결제는 저장소 테스트만으로 완료되지 않는다. 첫 적용 시 진행 중 요청을 유지한 전환, 의도적인 실패와 롤백, 두 노드의 자정 배치 지급 1회, 공개 로그인 유지와 백업 복구를 시험한다.
