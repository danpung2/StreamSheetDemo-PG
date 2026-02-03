#!/usr/bin/env bash
set -euo pipefail

# 트래픽 생성 실행 스크립트 (pg-main)
# Traffic generator runner (pg-main)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY_SCRIPT="$SCRIPT_DIR/scripts/pg_load_generator.py"

usage() {
  cat <<'EOF'
PG Demo Traffic Generator

설명 / Description:
  pg-main REST API를 대상으로 결제/환불 트래픽을 지속 생성합니다.
  Continuously generates realistic payment/refund traffic against pg-main REST APIs.

사용법 / Usage:
  ./traffic-generate.sh [options]

옵션 / Options:
  -h, --help
      사용법 출력 / Show help

  --base-url <url>
      pg-main base URL (default: http://localhost:8080)

  --duration-seconds <sec>
      실행 시간(초). 0이면 무한 실행 / Duration seconds. 0 = run until Ctrl+C (default: 0)

  --bootstrap-merchants <n>
      시작 시 생성할 가맹점 수 / Merchants to create at start (default: 50)

  --merchant-pool <n>
      가맹점 풀 크기 / Merchant pool size (default: 50)

  --refund-rate <rate>
      결제당 환불 확률(0~1) / Refund probability per payment (default: 0.08)

  --sleep-ms <ms>
      반복 기본 sleep(ms) / Base sleep between iterations in ms (default: 250)

  --jitter-ms <ms>
      추가 랜덤 지터(ms) / Extra random jitter in ms (default: 400)

  --timeout <sec>
      HTTP timeout seconds (default: 5.0)

  --seed <n>
      난수 시드 / Random seed (default: unset)

  --dry-run
      API 호출 없이 페이로드만 출력 / Print sample payloads only

예시 / Examples:
  # 기본 설정으로 무한 실행
  ./traffic-generate.sh

  # 10분 동안 실행
  ./traffic-generate.sh --duration-seconds 600

  # 환불 비율을 15%로
  ./traffic-generate.sh --refund-rate 0.15

  # 다른 pg-main 대상
  ./traffic-generate.sh --base-url http://localhost:8080
EOF
}

die() {
  echo "[error] $*" >&2
  echo >&2
  usage >&2
  exit 2
}

if [[ ! -f "$PY_SCRIPT" ]]; then
  die "missing script: $PY_SCRIPT"
fi

BASE_URL="http://localhost:8080"
DURATION_SECONDS="0"
BOOTSTRAP_MERCHANTS="50"
MERCHANT_POOL="50"
REFUND_RATE="0.08"
SLEEP_MS="250"
JITTER_MS="400"
TIMEOUT="5.0"
SEED=""
DRY_RUN="0"

# 간단 옵션 파서
# Simple option parser
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --base-url)
      [[ $# -ge 2 ]] || die "--base-url requires a value"
      BASE_URL="$2"
      shift 2
      ;;
    --duration-seconds)
      [[ $# -ge 2 ]] || die "--duration-seconds requires a value"
      DURATION_SECONDS="$2"
      shift 2
      ;;
    --bootstrap-merchants)
      [[ $# -ge 2 ]] || die "--bootstrap-merchants requires a value"
      BOOTSTRAP_MERCHANTS="$2"
      shift 2
      ;;
    --merchant-pool)
      [[ $# -ge 2 ]] || die "--merchant-pool requires a value"
      MERCHANT_POOL="$2"
      shift 2
      ;;
    --refund-rate)
      [[ $# -ge 2 ]] || die "--refund-rate requires a value"
      REFUND_RATE="$2"
      shift 2
      ;;
    --sleep-ms)
      [[ $# -ge 2 ]] || die "--sleep-ms requires a value"
      SLEEP_MS="$2"
      shift 2
      ;;
    --jitter-ms)
      [[ $# -ge 2 ]] || die "--jitter-ms requires a value"
      JITTER_MS="$2"
      shift 2
      ;;
    --timeout)
      [[ $# -ge 2 ]] || die "--timeout requires a value"
      TIMEOUT="$2"
      shift 2
      ;;
    --seed)
      [[ $# -ge 2 ]] || die "--seed requires a value"
      SEED="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="1"
      shift 1
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

PY_ARGS=(
  "--base-url" "$BASE_URL"
  "--duration-seconds" "$DURATION_SECONDS"
  "--bootstrap-merchants" "$BOOTSTRAP_MERCHANTS"
  "--merchant-pool" "$MERCHANT_POOL"
  "--refund-rate" "$REFUND_RATE"
  "--sleep-ms" "$SLEEP_MS"
  "--jitter-ms" "$JITTER_MS"
  "--timeout" "$TIMEOUT"
)

if [[ -n "$SEED" ]]; then
  PY_ARGS+=("--seed" "$SEED")
fi

if [[ "$DRY_RUN" == "1" ]]; then
  PY_ARGS+=("--dry-run")
fi

exec python3 "$PY_SCRIPT" "${PY_ARGS[@]}"
