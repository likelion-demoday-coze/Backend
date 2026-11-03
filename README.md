# 🐾 COZ:E Backend

> **Your Cozy Economy Guide**  
> 하루 5분, 편하고 쉽게 즐기는 경제 퀴즈 플랫폼

[서비스 바로가기](https://www.coze.kids/) · [API 문서](https://api.coze.kids/swagger-ui/index.html)

COZ:E는 경제 공부를 어렵게 느끼는 사용자가 매일 간단한 퀴즈를 풀며 경제 지식을 쌓을 수 있도록 돕는 게이미피케이션 기반 경제 학습 서비스입니다.

사용자는 경제 퀴즈를 풀어 자신의 주가와 캐릭터를 성장시키고, 연속 학습과 타임어택 랭킹을 통해 꾸준한 학습 습관을 만들 수 있습니다.

---

## 💡 Why COZ:E?

경제 공부는 필요하지만 어려운 용어와 긴 기사 때문에 시작하거나 꾸준히 이어가기 쉽지 않습니다.

COZ:E는 이러한 진입 장벽을 낮추기 위해 다음과 같은 경험을 제공합니다.

- 하루 5문제로 부담 없이 학습
- 퀴즈 정답에 따른 가상 주가 상승
- 주가에 따라 성장하는 고양이 캐릭터
- 연속 학습과 복구 시스템
- 타임어택과 사용자 랭킹
- 매일 제공되는 경제 트렌드 콘텐츠

단순히 문제를 푸는 것에 그치지 않고, 학습 결과가 주가·캐릭터·랭킹에 반영되는 구조를 통해 경제 공부를 지속할 동기를 제공합니다.

---

## ✨ 주요 기능

### 🔐 인증 및 회원 관리

- 카카오 OAuth2 로그인
- Spring Security 기반 인증 및 인가
- JDBC Session 기반 로그인 상태 관리
- 신규 회원 닉네임 등록 및 중복 검사
- 회원 정보와 마이페이지 조회
- 닉네임 변경 및 회원 탈퇴
- 탈퇴 회원의 기존 세션 차단
- 최초 온보딩 및 튜토리얼 완료 처리
- 내부 운영 계정의 랭킹 제외 처리

### 🧩 맛보기 퀴즈

- 로그인 전 고정 맛보기 퀴즈 제공
- 문제별 답안 제출 및 정답 확인
- 본 학습 전 서비스 경험 제공

### 📚 정규장 퀴즈

- 경제 분야별 퀴즈 카테고리 제공
- 일일 정규장 세션 생성
- 진행 중인 학습 세션 이어 풀기
- 원본 문제 답안 제출 및 오답 재풀이
- 세션 완료와 학습 결과 조회
- 일반 회원과 패스 회원의 주가 반영 기회 차등 적용
- 정규장 원본 문제 완료를 기준으로 연속 학습 인정

### ⏱ 타임어택 퀴즈

- 제한 시간 내 연속 문제 풀이
- 일일 참여 가능 횟수 관리
- 정답 수와 기록을 기준으로 일일 랭킹 산정
- 타임어택 결과 및 최고 기록 조회
- 당일 랭킹 초기화 및 전날 보상 확정

### 📈 내 주가 및 캐릭터 성장

- 퀴즈 정답에 따른 가상 주가 상승
- 미학습 시 정책에 따른 주가 하락
- 일별 주가 스냅샷 및 변동 내역 저장
- 주가 구간에 따른 캐릭터 단계 관리
- 미확인 진화 단계 조회 및 확인 처리
- 캐릭터 성장 단계별 연출 지원

### 🔥 연속 학습 및 복구

- 정규장 완료를 기준으로 연속 학습 일수 계산
- 주간 학습 발자국과 현재 스트릭 조회
- 학습 중단 시 최초 1회 주가 하락
- 연속 학습 복구 이벤트 생성
- 일반 회원의 복구권 사용
- 패스 회원의 무료 복구 지원
- 복구 대기·완료·만료 상태 관리
- 복구 완료 시 연속 일수, 주가, 캐릭터 단계 복원

### 🐟 게임 내 재화와 상점

- 출석 및 랭킹 보상으로 FISH 지급
- 정규장과 타임어택 입장 시 FISH 사용
- FISH 잔액 및 증감 내역 조회
- 상점 아이템 목록 조회
- 연속 학습 복구권 구매
- 회원별 보유 아이템과 수량 관리

### 🏆 랭킹 및 보상

- 누적 주가 랭킹 조회
- 오늘의 타임어택 랭킹 조회
- 자정 기준 랭킹 확정
- 주가 및 타임어택 상위 회원 보상 지급
- 미확인 랭킹 보상 팝업 조회
- 랭킹 보상 확인 처리
- 내부 운영 계정을 순위와 보상 대상에서 제외
- 과거 미정산 날짜 자동 탐색 및 재정산

### 📰 오늘의 경제 트렌드

- 당일 경제 트렌드 콘텐츠 조회
- 경제 트렌드 상세 조회
- Liner API 기반 트렌드 콘텐츠 생성
- 콘텐츠 검증 및 저장
- 생성 실패 시 예약된 재시도 처리
- 분산 환경에서 중복 실행을 막기 위한 ShedLock 적용

### 💳 일주일 패스 및 결제

- 일주일 패스 상품 조회
- PayApp 결제 주문 생성
- 결제창 이탈 후 진행 중 결제 재진입
- PayApp Feedback 검증 및 멱등 처리
- 결제 완료 후 일주일 패스 지급
- 패스 지급 실패 재처리
- 결제 내역 및 상세 조회
- 결제 취소와 패스 회수
- 결제 취소 상태 조정 및 재처리
- 활성 패스와 패스 이용 내역 조회
- 결제 기능 비활성화 시 관련 API 안전 차단

---

## 🛠 Tech Stack

| Category | Stack |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.1.1 |
| Web | Spring Web MVC, RestClient |
| ORM | Spring Data JPA |
| Database | MySQL |
| Security | Spring Security, OAuth2 Client |
| Session | Spring Session JDBC |
| Validation | Jakarta Validation |
| Lock | ShedLock |
| Payment | PayApp |
| Content API | Liner API |
| API Docs | Swagger, Springdoc OpenAPI |
| Monitoring | Spring Boot Actuator, Micrometer |
| Observability | OpenTelemetry, Prometheus, Loki, Tempo, Grafana |
| Build | Gradle |
| Test | JUnit 5, H2, Testcontainers |
| Infra | Docker, Docker Compose, Nginx |
| CI/CD | GitHub Actions, GHCR |
| Deploy | AWS EC2, Blue-Green Deployment |

---

## 🏗 Backend Architecture

COZ:E Backend는 기능별 책임을 명확하게 분리하기 위해 도메인 중심 패키지 구조를 사용합니다.

```text
src/main/java/demoday/backend
├── activity
├── attendance
├── auth
├── character
├── dailyquiz
├── fish
├── member
├── payment
├── previewquiz
├── quiz
├── ranking
├── stock
├── store
├── streak
├── timeattack
├── trend
└── global
    ├── api
    ├── config
    ├── exception
    ├── logging
    ├── security
    └── transaction
```

각 도메인은 필요에 따라 다음 구조로 구성됩니다.

```text
{domain}
├── client
├── code
├── config
├── controller
├── domain
├── dto
├── logging
├── metric
├── policy
├── repository
├── scheduler
└── service
```

공통 API 응답, 예외 처리, 보안, 시간 설정, 로깅 및 트랜잭션 재시도 기능은 `global` 영역에서 관리합니다.

주가 계산, 캐릭터 진화, 랭킹 정산, 연속 학습 복구, 결제 처리 등의 핵심 정책은 각 도메인 내부의 서비스와 정책 객체로 분리했습니다.

---

## 🔄 Core Service Flow

### 정규장 학습 흐름

```text
정규장 세션 생성
        ↓
카테고리별 원본 문제 제공
        ↓
답안 제출 및 정답 판정
        ↓
정답에 따른 주가 상승
        ↓
캐릭터 단계 및 진화 상태 갱신
        ↓
원본 문제 전체 제출
        ↓
정규장 완료 및 연속 학습 인정
```

### 연속 학습 복구 흐름

```text
정규장 미학습
        ↓
자정 스케줄러의 학습 중단 판정
        ↓
주가 하락 및 복구 이벤트 생성
        ↓
복구권 또는 패스 혜택 사용
        ↓
복구 대기
        ↓
당일 정규장 완료
        ↓
연속 일수·주가·캐릭터 단계 복원
```

### 랭킹 및 보상 흐름

```text
당일 학습 및 타임어택 기록
        ↓
일일 주가 스냅샷 저장
        ↓
자정 랭킹 확정
        ↓
상위 1~3위 보상 지급
        ↓
미확인 랭킹 보상 생성
        ↓
사용자 보상 팝업 조회 및 확인
```

### 결제 및 패스 지급 흐름

```text
서버 상품 정보로 주문 생성
        ↓
PayApp 결제창 이동
        ↓
PayApp Feedback 수신
        ↓
Feedback 인증 및 주문 정보 검증
        ↓
결제 승인 상태 저장
        ↓
일주일 패스 지급
        ↓
결제 완료 처리
        ↓
지급 실패 시 재처리
```

---

## 👥 Members

| Member | Contribution |
| --- | --- |
| 주해윤 ([@uaeoon](https://github.com/uaeoon)) | 데일리·맛보기·타임어택 퀴즈, 랭킹·보상, 회원·마이페이지, 결제 상품·페이앱 결제·일주일 패스, 결제 관측성 및 담당 기능 테스트 |
| 이한재 ([@leehanjay](https://github.com/leehanjay)) | 생선·주가, 출석 보상, 연속 학습 복구, 상점·아이템, 경제 트렌드, 스케줄러, 데이터베이스 및 CI/CD·배포 환경 관리 |

---

## 📚 API Documentation

Swagger를 통해 주요 Backend API의 요청과 응답 명세를 확인할 수 있습니다.

- **Production Swagger:** [https://api.coze.kids/swagger-ui/index.html](https://api.coze.kids/swagger-ui/index.html)

| Domain | 주요 기능 |
| --- | --- |
| Auth | 카카오 로그인, CSRF 조회, 회원가입, 로그아웃 |
| Member | 내 정보, 닉네임, 튜토리얼, 회원 탈퇴 |
| Preview Quiz | 맛보기 문제 및 답안 제출 |
| Daily Quiz | 카테고리, 세션, 답안, 완료, 결과 |
| Time Attack | 참여 상태, 세션, 문제, 답안, 완료, 결과 |
| Character | 현재 캐릭터와 진화 상태 조회·확인 |
| Stock | 현재 주가, 그래프, 변동 내역 |
| Attendance | 출석 보상 상태 및 수령 |
| Fish | FISH 잔액 및 거래 내역 |
| Store | 상품 목록, 아이템 구매 및 보유 내역 |
| Streak | 연속 학습 현황, 복구 이벤트 및 복구권 사용 |
| Ranking | 주가·타임어택 랭킹 및 랭킹 보상 |
| Trend | 오늘의 경제 트렌드 및 상세 콘텐츠 |
| Product | 판매 중인 결제 상품 조회 |
| Payment | 주문, Feedback, 상태, 내역, 취소 |
| Pass | 현재 패스 및 패스 이용 내역 |

---

## 📊 Observability

운영 장애를 빠르게 파악하고 결제 흐름을 추적하기 위해 로그·지표·트레이스를 통합 관리합니다.

### 로그

- SLF4J 기반 구조화 로그
- 요청별 `requestId` MDC 적용
- 스케줄러별 `jobRunId` 적용
- 결제번호, 회원 ID, 결제 ID 중심의 검색 가능한 로그
- 민감정보와 전체 PG 요청·응답 로그 제외
- 반복 지급 실패 로그 Throttling

### 지표

- 결제 주문 생성 수
- Feedback 처리 성공·실패·거절 수
- 결제 처리 시간
- 상품 지급 성공·실패 수
- 결제 취소 성공·실패 수
- 전체 API 요청량과 5xx 비율

### 운영 모니터링

```text
Spring Boot Application
        ↓
OpenTelemetry
        ↓
Grafana Cloud
        ├── Prometheus Metrics
        ├── Loki Logs
        └── Tempo Traces
        ↓
Grafana Dashboard
        ↓
Discord Alert
```

상품 지급 실패와 Feedback 처리 장애는 `critical`, 결제 취소 실패는 `warning` 수준으로 알림을 전송합니다.

---

## 🚀 Deployment

```text
main 브랜치 Merge
        ↓
GitHub Actions CI
        ├── 배포 스크립트 검증
        ├── 테스트 실행
        ├── Gradle Build
        └── Docker Build 검증
        ↓
GitHub Container Registry
        ↓
AWS EC2
        ↓
Blue-Green Deployment
        ↓
Smoke Test
        ↓
Nginx Upstream 전환
        ↓
Spring Boot Server
```

배포 과정에서 기존 인스턴스를 즉시 종료하지 않고 신규 컨테이너의 상태를 확인한 후 트래픽을 전환합니다.

장애가 발생하면 이전 배포 버전으로 롤백할 수 있도록 운영 스크립트를 관리합니다.

---

## 🌿 Branch Convention

브랜치 이름은 다음 규칙을 따릅니다.

```text
type/이슈번호-작업내용
```

| Type | 의미 | 예시 |
| --- | --- | --- |
| feat | 새로운 기능 개발 | `feat/1-signup` |
| fix | 버그 수정 | `fix/2-login-error` |
| docs | 문서 수정 | `docs/3-readme` |
| refactor | 코드 리팩토링 | `refactor/4-member-service` |
| chore | 설정, 빌드, 기타 작업 | `chore/5-github-template` |
| test | 테스트 코드 작성 | `test/6-member-service` |

---

## 💬 Commit Convention

커밋 메시지는 다음 형식을 사용합니다.

```text
type: 작업 내용
```

| Type | 의미 |
| --- | --- |
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| docs | 문서 수정 |
| style | 코드 포맷팅 등 기능 변화 없는 수정 |
| refactor | 코드 리팩토링 |
| test | 테스트 코드 추가 또는 수정 |
| chore | 빌드 설정, 패키지 설정, 기타 작업 |
| rename | 파일 또는 폴더명 변경 |
| remove | 파일 삭제 |

---

## 🔗 Links

- **Service:** [https://www.coze.kids/](https://www.coze.kids/)
- **Instagram:** [https://www.instagram.com/cozeconomy?mdxt=djVsaTBtaTFyZXI%3D&utm_source=qr](https://www.instagram.com/cozeconomy?mdxt=djVsaTBtaTFyZXI%3D&utm_source=qr)
- **Kakaotalk Channel** [https://pf.kakao.com/_ngMWX](https://pf.kakao.com/_ngMWX)
- **Backend Repository:** [https://github.com/likelion-demoday-coze/Backend](https://github.com/likelion-demoday-coze/Backend)
