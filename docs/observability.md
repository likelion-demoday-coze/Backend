# 로컬 OpenTelemetry·LGTM 검증

`grafana/otel-lgtm`은 로컬 개발·검증 전용으로 사용한다. 운영에서는 동일한 컨테이너를 백엔드 서버에 함께 올리지 않고, 별도의 OpenTelemetry Collector 또는 Grafana Cloud로 OTLP 데이터를 전송한다.

## 실행

1. `.env.example`을 참고해 `.env`를 준비한다.
2. 기본 서비스와 로컬 LGTM 확장 구성을 함께 실행한다.

```bash
docker compose \
  --env-file .env \
  -f docker-compose.yaml \
  -f docker-compose.observability.yaml \
  up -d --build
```

로컬 주소는 다음과 같다.

- 백엔드: `http://localhost:8080`
- Actuator: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- OTLP HTTP: `http://localhost:4318`
- OTLP gRPC: `http://localhost:4317`

Grafana의 로컬 기본 계정은 `.env`의 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`를 사용한다. 값을 지정하지 않으면 둘 다 `admin`이다.

## 수집 확인

1. 백엔드 API를 한 번 이상 호출한다.
2. Grafana `Explore`에서 다음 데이터 소스를 각각 선택한다.
3. 서비스 이름 `demoday-backend`로 조회한다.

확인 대상:

- Metrics: `http_server_requests_seconds_count`, `payment_order_created_total`
- Logs: `service_name = demoday-backend`인 애플리케이션 로그
- Traces: `service.name = demoday-backend`인 HTTP 요청 트레이스
- 연결성: 로그의 `traceId`와 같은 트레이스를 조회할 수 있어야 한다.

페이앱 호출을 테스트할 수 있는 환경에서는 서버 트레이스 아래에 외부 HTTP 클라이언트 span이 생성되는지도 확인한다.

## 종료

```bash
docker compose \
  --env-file .env \
  -f docker-compose.yaml \
  -f docker-compose.observability.yaml \
  down
```

## 운영 전달값

배포 환경에서는 다음 값을 운영 Collector 주소로 설정한다.

```text
OTEL_ENABLED=true
OTEL_SERVICE_NAME=demoday-backend
OTEL_ENVIRONMENT=production
OTEL_TRACING_SAMPLING_PROBABILITY=0.1
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=<collector>/v1/traces
OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=<collector>/v1/logs
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=<collector>/v1/metrics
```

Collector가 인증을 요구하면 인증 헤더는 배포 환경의 비밀값으로 관리한다. 저장소나 이미지에 포함하지 않는다.

