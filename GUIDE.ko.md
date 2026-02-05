# GUIDE (운영 환경 가정)

이 문서는 **운영 환경을 가정한 형태**로 StreamSheet 테스트 데이터를 구성하는 절차를 설명합니다.

## 목표

- (1) DB 준비
- (2) 초기 마스터 데이터(본사/업체/일부 트랜잭션 등) 시딩
- (3) 운영처럼 트랜잭션이 지속적으로 유입되도록 생성
- (4) Transactions Export(StreamSheet 엑셀 내보내기)로 결과를 확인

## 1) 개발 환경 실행(docker compose)

아래 명령으로 개발용 스택을 한번에 올립니다.

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

동작 개요
- Postgres/Mongo를 컨테이너로 실행
- `pg-admin` seeder 프로필로 초기 데이터 시딩(1회 실행 후 종료)
- `pg-main`/`pg-admin` 실행
- `traffic`가 `./traffic-generate.sh`를 백그라운드 실행하여 운영 유입처럼 트랜잭션 생성

접근
- pg-admin(UI): `http://localhost:8081`
- pg-main(API): docker 네트워크 내부 전용(외부 접근 불가)

## 2) (옵션) 초기 데이터 재시딩(pg-admin seeder profile)

docker compose로 스택을 올릴 때는 기본적으로 시딩이 자동으로 수행됩니다.
데이터를 초기화하고 다시 시딩하고 싶다면 로컬에서 seeder 프로필을 실행합니다.

```bash
./gradlew :pg-admin:bootRun --args='--spring.profiles.active=seeder --pgdemo.seeder.reset=true'
```

참고
- `reset=true`는 트랜잭션/머천트/본사 데이터를 정리하고 재시딩합니다.
- `admin_user`는 삭제하지 않도록 구성되어 있어, 운영사 기본 관리자 계정이 유지됩니다.

## 3) (옵션) 애플리케이션 직접 실행(pg-main + pg-admin)

docker compose 대신 로컬에서 직접 실행하는 경우, 각 모듈을 일반 프로필로 실행합니다.

```bash
./gradlew :pg-main:bootRun
```

다른 터미널에서:
```bash
./gradlew :pg-admin:bootRun
```

- pg-main: `http://localhost:8080`
- pg-admin: `http://localhost:8081`

## 4) (옵션) 운영 가정 트랜잭션 생성(traffic-generate.sh)

`traffic-generate.sh`는 pg-main REST API를 대상으로 결제/환불 트랜잭션을 지속 생성합니다.

docker compose를 사용하는 경우, `traffic` 서비스가 기본 설정으로 자동 실행됩니다.
직접 실행하고 싶다면 아래를 사용합니다.

```bash
./traffic-generate.sh
```

옵션 예시
- 실행 시간을 제한: `./traffic-generate.sh --duration-seconds 300`
- 생성 속도 조절: `./traffic-generate.sh --sleep-ms 200 --jitter-ms 300`
- 환불 비율 조절: `./traffic-generate.sh --refund-rate 0.05`
- 트랜잭션을 생성할 업체 풀 크기: `./traffic-generate.sh --merchant-pool 200`

주의
- 이 스크립트는 **트랜잭션 generate**만 수행합니다(본사/업체 추가 생성은 하지 않습니다).
- 이미 2)에서 DataSeeder를 실행했다면, 일반적으로는 트랜잭션 생성만 수행하는 것이 “운영 유입” 가정에 더 가깝습니다.

## 5) Exports(StreamSheet) 가이드

이 프로젝트의 Export는 StreamSheet 기반으로 **Transactions(결제+환불) 엑셀 파일**을 생성합니다.

### 5.1 로그인

pg-admin 로그인 페이지로 접속합니다.
- URL: `http://localhost:8081/login`

기본 로그인 계정(도커 초기 데이터 + DataSeeder 보정)

| 구분 | 역할 | 이메일 | 비밀번호 | 비고 |
| --- | --- | --- | --- | --- |
| 운영사(OPERATOR) | 관리자(ADMIN) | `admin@pgdemo.com` | `admin123!` | 도커 초기 데이터 |
| 운영사(OPERATOR) | 매니저(MANAGER) | - | - | 기본 제공 없음(프로비저닝으로 생성) |
| 운영사(OPERATOR) | 뷰어(VIEWER) | - | - | 기본 제공 없음(현재 프로비저닝 API 미지원) |
| 본사(HEADQUARTERS) | 관리자(ADMIN) | `hq_admin@pgdemo.com` | `password123!` | DataSeeder에서 보장 |
| 본사(HEADQUARTERS) | 매니저(MANAGER) | - | - | 기본 제공 없음(프로비저닝으로 생성) |
| 본사(HEADQUARTERS) | 뷰어(VIEWER) | - | - | 기본 제공 없음(현재 프로비저닝 API 미지원) |
| 업체(MERCHANT) | 관리자(ADMIN) | `merchant_admin@pgdemo.com` | `password123!` | DataSeeder에서 보장 |
| 업체(MERCHANT) | 매니저(MANAGER) | - | - | 기본 제공 없음(프로비저닝으로 생성) |
| 업체(MERCHANT) | 뷰어(VIEWER) | - | - | 기본 제공 없음(현재 프로비저닝 API 미지원) |

참고
- 위 계정들은 2) 시딩 단계에서 생성/보정됩니다(이미 존재하더라도 비밀번호/상태가 맞춰집니다).

### 5.2 Export 요청 및 다운로드(UI)

1) 메뉴에서 `Exports`로 이동
- URL: `http://localhost:8081/admin/exports/payments`

2) 기간(from/to) 및 HQ/Merchant 필터를 설정하고 `Request export` 클릭

3) Job 상태가 `COMPLETED`가 되면 `Download` 버튼으로 엑셀 파일 다운로드

### 5.3 Export가 비어있거나 실패하는 경우

- 트랜잭션이 실제로 생성되고 있는지 먼저 확인합니다.
  - `./traffic-generate.sh` 실행 로그에서 payment/refund ok 카운터 증가 확인
- Export Job 상태가 `FAILED`이면 에러 요약을 확인합니다.
