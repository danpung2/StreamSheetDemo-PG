#!/usr/bin/env python3

import argparse
import datetime as dt
import json
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


_HTTP_RETRIES = 20
_HTTP_RETRY_SLEEP_MS = 250
_HTTP_RETRY_MAX_SLEEP_MS = 2000


def _iso_date(d: dt.date) -> str:
    return d.isoformat()


def _now_ms() -> int:
    return int(time.time() * 1000)


def _rand_choice(rng: random.Random, items):
    return items[rng.randrange(0, len(items))]


def _http_json(method: str, url: str, payload, timeout_s: float, headers=None):
    body = None
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
    req = urllib.request.Request(url=url, data=body, method=method)
    req.add_header("Accept", "application/json")
    if payload is not None:
        req.add_header("Content-Type", "application/json")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)

    sleep_ms = int(_HTTP_RETRY_SLEEP_MS or 0)
    max_sleep_ms = int(_HTTP_RETRY_MAX_SLEEP_MS or 0)
    max_retries = int(_HTTP_RETRIES or 0)

    attempt = 0
    while True:
        try:
            with urllib.request.urlopen(req, timeout=timeout_s) as resp:
                raw = resp.read()
                text = raw.decode("utf-8") if raw else ""
                return resp.status, text
        except urllib.error.HTTPError as e:
            raw = e.read()
            text = raw.decode("utf-8") if raw else ""
            if e.code >= 500 and attempt < max_retries:
                if sleep_ms > 0:
                    time.sleep((sleep_ms / 1000.0) + (random.random() * 0.05))
                    if max_sleep_ms > 0:
                        sleep_ms = min(sleep_ms * 2, max_sleep_ms)
                    else:
                        sleep_ms = sleep_ms * 2
                attempt += 1
                continue
            return e.code, text
        except (
            urllib.error.URLError,
            TimeoutError,
            ConnectionResetError,
            OSError,
        ) as e:
            if attempt < max_retries:
                if sleep_ms > 0:
                    time.sleep((sleep_ms / 1000.0) + (random.random() * 0.05))
                    if max_sleep_ms > 0:
                        sleep_ms = min(sleep_ms * 2, max_sleep_ms)
                    else:
                        sleep_ms = sleep_ms * 2
                attempt += 1
                continue
            return 0, str(e)


def _bearer(token: str) -> str:
    return "Bearer " + token


def _issue_operator_api_key(
    admin_base_url: str, email: str, password: str, timeout_s: float
):
    admin_base = admin_base_url.rstrip("/")
    login_url = admin_base + "/api/auth/login"
    status, text = _http_json(
        "POST",
        login_url,
        {"email": email, "password": password},
        timeout_s,
    )
    if status != 200:
        raise RuntimeError(
            f"admin login failed status={status} url={login_url} body={text}"
        )

    login = _safe_json(text) or {}
    access_token = login.get("accessToken")
    if not access_token:
        raise RuntimeError(f"admin login missing accessToken body={text}")

    key_url = admin_base + "/api/v1/provisioning/api-keys"
    status, text = _http_json(
        "POST",
        key_url,
        {"tenantType": "OPERATOR", "tenantId": None, "name": "pg_load_generator"},
        timeout_s,
        headers={"Authorization": _bearer(access_token)},
    )
    if status != 200:
        raise RuntimeError(
            f"api key issue failed status={status} url={key_url} body={text}"
        )

    data = _safe_json(text) or {}
    api_key = data.get("apiKey")
    if not api_key:
        raise RuntimeError(f"api key issue missing apiKey body={text}")

    return api_key


def _http_json_or_die(method: str, url: str, payload, timeout_s: float):
    status, text = _http_json(method, url, payload, timeout_s)
    if status < 200 or status >= 300:
        raise RuntimeError(f"http fail status={status} url={url} body={text}")
    return text


def _safe_json(text: str):
    if not text:
        return None
    try:
        return json.loads(text)
    except Exception:
        return None


def _format_amount_krw(amount: int) -> str:
    return f"{amount:,}"


def build_merchant_payload(
    rng: random.Random, merchant_code: str, headquarters_id: str
):
    business_types = [
        "CAFE",
        "RESTAURANT",
        "FAST_FOOD",
        "CONVENIENCE_STORE",
        "RETAIL",
        "OTHER",
    ]
    store_types = ["FRANCHISE", "DIRECT"]

    company_names = [
        # Coffee & Cafe
        "스타벅스",
        "투썸플레이스",
        "이디야커피",
        "메가MGC커피",
        "컴포즈커피",
        "빽다방",
        "폴바셋",
        "할리스",
        "엔제리너스",
        "파스쿠찌",
        "커피빈",
        "탐앤탐스",
        "매머드커피",
        "더벤티",
        "공차",
        "카페베네",
        "드롭탑",
        "블루보틀",
        "설빙",
        "쥬씨",
        # Fast Food & Burger
        "맥도날드",
        "버거킹",
        "롯데리아",
        "KFC",
        "맘스터치",
        "노브랜드버거",
        "쉐이크쉑",
        "프랭크버거",
        "모스버거",
        "타코벨",
        "이삭토스트",
        "에그드랍",
        "서브웨이",
        "퀴즈노스",
        "봉구스밥버거",
        # Bakery & Dessert
        "파리바게뜨",
        "뚜레쥬르",
        "던킨",
        "크리스피크림도넛",
        "배스킨라빈스",
        "성심당",
        "이성당",
        "삼송빵집",
        "태극당",
        "옵스",
        # Chicken & Pizza
        "BBQ",
        "BHC",
        "교촌치킨",
        "굽네치킨",
        "네네치킨",
        "처갓집양념치킨",
        "페리카나",
        "멕시카나",
        "60계치킨",
        "노랑통닭",
        "도미노피자",
        "피자헛",
        "파파존스",
        "미스터피자",
        "피자알볼로",
        "피자스쿨",
        "청년피자",
        "반올림피자",
        "피자마루",
        "59쌀피자",
        # Retail & CVS
        "CU",
        "GS25",
        "세븐일레븐",
        "이마트24",
        "미니스톱",
        "올리브영",
        "다이소",
        "롯데마트",
        "이마트",
        "홈플러스",
        "코스트코",
        "하나로마트",
        "하이마트",
        "전자랜드",
        "ABC마트",
        # Tech & Platform
        "쿠팡",
        "네이버",
        "카카오",
        "배달의민족",
        "요기요",
        "마켓컬리",
        "무신사",
        "29CM",
        "에이블리",
        "지그재그",
        "토스",
        "당근마켓",
        "야놀자",
        "여기어때",
        "쏘카",
        # Fashion & Others
        "나이키",
        "아디다스",
        "유니클로",
        "자라",
        "H&M",
        "스파오",
        "탑텐",
        "에잇세컨즈",
        "CGV",
        "롯데시네마",
        "메가박스",
        "교보문고",
        "영풍문고",
        "알라딘",
        "예스24",
    ]

    branch_districts = [
        "강남",
        "홍대",
        "신촌",
        "명동",
        "종로",
        "이태원",
        "압구정",
        "여의도",
        "광화문",
        "잠실",
    ]
    branch_suffixes = [
        "점",
        "역점",
        "DT점",
        "센터점",
        "타워점",
        "몰점",
        "캠퍼스점",
    ]

    brand = _rand_choice(rng, company_names)
    district = _rand_choice(rng, branch_districts)

    # Keep naming realistic: "본점" exists but should be rare.
    # 매장명은 현실적으로: "본점"은 드물게만 생성
    if rng.random() < 0.02:
        name = f"{brand} {district}본점"
    else:
        suffix = _rand_choice(rng, branch_suffixes)
        name = f"{brand} {district}{suffix}"

    start = dt.date.today() - dt.timedelta(days=rng.randint(0, 365 * 2))
    end = start + dt.timedelta(days=rng.randint(30, 365 * 3))

    business_type = _rand_choice(rng, business_types)

    return {
        "headquartersId": headquarters_id,
        "merchantCode": merchant_code,
        "name": name,
        "storeType": _rand_choice(rng, store_types),
        "businessType": business_type,
        "contractStartDate": _iso_date(start),
        "contractEndDate": _iso_date(end),
        "storeNumber": rng.randint(1, 9999),
    }


def build_payment_payload(rng: random.Random, merchant_id: str, merchant_code: str):
    payment_methods = ["CARD", "TRANSFER", "VIRTUAL_ACCOUNT"]
    # realistic-ish KRW amounts: skew small, sometimes larger
    bucket = rng.random()
    if bucket < 0.70:
        amount = rng.randrange(3500, 55000, 100)
    elif bucket < 0.93:
        amount = rng.randrange(55000, 220000, 100)
    else:
        amount = rng.randrange(220000, 1800000, 1000)

    order_id = f"ORD{time.time_ns()}-{rng.randint(1, 999999999):09d}"

    return {
        "merchantId": merchant_id,
        "orderId": order_id,
        "amount": int(amount),
        "paymentMethod": _rand_choice(rng, payment_methods),
    }


def choose_payment_outcome(rng: random.Random):
    """Pick a realistic payment lifecycle outcome (state machine target)."""

    roll = rng.randint(0, 999)
    if roll <= 974:
        return "PAYMENT_COMPLETED", None
    if roll <= 976:
        return (
            "PAYMENT_FAILED",
            _rand_choice(
                rng,
                [
                    "잔액 부족",
                    "카드 한도 초과",
                    "통신 오류",
                    "결제 거절",
                    "유효하지 않은 카드",
                ],
            ),
        )
    if roll <= 991:
        return "PAYMENT_PROCESSING", None
    if roll <= 995:
        return "PAYMENT_PENDING", None
    return "PAYMENT_CANCELLED", None


def get_refund_reason(rng: random.Random, business_type: str):
    common_reasons = ["고객 변심", "주문 취소", "중복 결제", "기타 사유", None]

    fnb_reasons = [
        "음식 상태 불량",
        "메뉴 오제조",
        "배달 지연",
        "이물질 혼입",
        "포장 불량",
        "맛 불만족",
    ] + common_reasons

    retail_reasons = [
        "상품 불량",
        "사이즈 교환",
        "색상 교환",
        "오배송",
        "마감 미흡",
        "유통기한 경과",
    ] + common_reasons

    if business_type in ["CAFE", "RESTAURANT", "FAST_FOOD"]:
        target_list = fnb_reasons
    elif business_type in ["RETAIL", "CONVENIENCE_STORE"]:
        target_list = retail_reasons
    else:
        # OTHER or unknown: combine both
        target_list = fnb_reasons + retail_reasons

    return _rand_choice(rng, target_list)


def build_refund_payload(rng: random.Random, payment_amount: int, business_type: str):
    # 70% partial refund, 30% full
    if rng.random() < 0.30:
        refund_amount = payment_amount
    else:
        # partial: 10% ~ 90%, rounded to 100
        ratio = rng.uniform(0.10, 0.90)
        refund_amount = int(max(100, int((payment_amount * ratio) // 100 * 100)))
        refund_amount = min(refund_amount, payment_amount)

    return {
        "refundAmount": int(refund_amount),
        "refundReason": get_refund_reason(rng, business_type),
    }


def choose_refund_outcome(rng: random.Random):
    """Pick a realistic refund lifecycle outcome (state machine target)."""

    roll = rng.randint(0, 999)
    if roll <= 959:
        return "REFUND_COMPLETED", None
    if roll <= 964:
        return (
            "REFUND_FAILED",
            _rand_choice(
                rng,
                [
                    "환불 기간 초과",
                    "계좌 정보 오류",
                    "시스템 오류",
                    "환불 불가 상품",
                ],
            ),
        )
    if roll <= 984:
        return "REFUND_PROCESSING", None
    return "REFUND_PENDING", None


def main(argv):
    p = argparse.ArgumentParser(
        description="PG demo load generator: continuously create merchants/payments/refunds via pg-main REST APIs"
    )
    p.add_argument(
        "--base-url", default="http://localhost:8080", help="pg-main base URL"
    )
    p.add_argument(
        "--admin-url",
        default="http://localhost:8081",
        help="pg-admin base URL (for API key issuance)",
    )
    p.add_argument("--timeout", type=float, default=5.0, help="HTTP timeout seconds")
    p.add_argument(
        "--http-retries",
        type=int,
        default=20,
        help="HTTP request retries on network/5xx errors (default: 20)",
    )
    p.add_argument(
        "--http-retry-sleep-ms",
        type=int,
        default=250,
        help="Initial retry sleep in ms (default: 250)",
    )
    p.add_argument(
        "--http-retry-max-sleep-ms",
        type=int,
        default=2000,
        help="Max retry sleep in ms (default: 2000)",
    )
    p.add_argument("--seed", type=int, default=None, help="random seed")

    p.add_argument(
        "--mode",
        choices=["seed-generate", "seed", "generate"],
        default="generate",
        help="seed = bootstrap HQ/merchants (internal API), generate = create payments/refunds, seed-generate = both",
    )

    p.add_argument(
        "--hq",
        "--seed-headquarters",
        dest="seed_headquarters",
        type=int,
        default=5,
        help="headquarters count for internal seed (default: 5)",
    )
    p.add_argument(
        "--m-per-hq",
        "--seed-merchants-per-hq",
        dest="seed_merchants_per_hq",
        type=int,
        default=10,
        help="merchants per HQ for internal seed (default: 10)",
    )

    p.add_argument(
        "--operator-api-key",
        default=None,
        help="operator API key for /api/v1/internal/* (X-API-KEY). If omitted, script will issue one via pg-admin.",
    )
    p.add_argument(
        "--operator-email",
        default="admin@pgdemo.com",
        help="operator admin email (used to issue operator API key)",
    )
    p.add_argument(
        "--operator-password",
        default="admin123!",
        help="operator admin password (used to issue operator API key)",
    )

    p.add_argument(
        "--merchant-pool",
        type=int,
        default=50,
        help="number of merchants to keep in memory",
    )
    p.add_argument(
        "--refund-rate",
        type=float,
        default=0.08,
        help="probability of refund per payment (0~1)",
    )
    p.add_argument(
        "--sleep-ms", type=int, default=250, help="base sleep between iterations"
    )
    p.add_argument(
        "--jitter-ms", type=int, default=400, help="extra random sleep jitter"
    )
    p.add_argument(
        "--duration-seconds", type=int, default=0, help="0 = run until Ctrl+C"
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="do not call APIs; just print example payloads",
    )

    args = p.parse_args(argv)

    global _HTTP_RETRIES, _HTTP_RETRY_SLEEP_MS, _HTTP_RETRY_MAX_SLEEP_MS
    _HTTP_RETRIES = int(args.http_retries)
    _HTTP_RETRY_SLEEP_MS = int(args.http_retry_sleep_ms)
    _HTTP_RETRY_MAX_SLEEP_MS = int(args.http_retry_max_sleep_ms)

    rng = random.Random(args.seed)
    base = args.base_url.rstrip("/")

    endpoints = {
        "seed_bootstrap": base + "/api/v1/internal/seed/bootstrap",
        "merchant_list": base + "/api/v1/merchants",
        "payment_create": base + "/api/v1/payments",
        "payment_process": base + "/api/v1/payments/{id}/process",
        "payment_complete": base + "/api/v1/payments/{id}/complete",
        "payment_fail": base + "/api/v1/payments/{id}/fail",
        "payment_cancel": base + "/api/v1/payments/{id}/cancel",
        "refund_create": base + "/api/v1/payments/{id}/refund",
        "refund_process": base + "/api/v1/refunds/{id}/process",
        "refund_complete": base + "/api/v1/refunds/{id}/complete",
        "refund_fail": base + "/api/v1/refunds/{id}/fail",
    }

    merchant_pool = []  # list of dict: {id, merchantCode, name, businessType}

    def log(msg: str):
        sys.stdout.write(msg + "\n")
        sys.stdout.flush()

    def sleep_small_ms():
        # Small per-transition delay to ensure realistic event timestamps.
        time.sleep((20 + rng.randint(0, 180)) / 1000.0)

    if args.dry_run:
        sample_code = f"M{rng.randint(1, 99999999):08d}"
        seed_payload = {
            "headquartersCount": int(args.seed_headquarters),
            "merchantsPerHeadquarters": int(args.seed_merchants_per_hq),
            "seed": args.seed,
        }
        log("[dry-run] seed payload: " + json.dumps(seed_payload, ensure_ascii=True))
        m = build_merchant_payload(rng, sample_code, "<headquartersId>")
        log("[dry-run] merchant payload: " + json.dumps(m, ensure_ascii=True))
        log(
            "[dry-run] payment payload: "
            + json.dumps(
                build_payment_payload(rng, "<merchantId>", sample_code),
                ensure_ascii=True,
            )
        )
        log(
            "[dry-run] refund payload: "
            + json.dumps(
                build_refund_payload(rng, 15000, m.get("businessType", "OTHER")),
                ensure_ascii=True,
            )
        )
        return 0

    if args.mode in ["seed-generate", "seed"]:
        operator_api_key = args.operator_api_key
        if not operator_api_key:
            operator_api_key = _issue_operator_api_key(
                admin_base_url=args.admin_url,
                email=args.operator_email,
                password=args.operator_password,
                timeout_s=args.timeout,
            )
            log("[seed] operator api key issued; prefix=" + operator_api_key[:12])

        seed_payload = {
            "headquartersCount": int(args.seed_headquarters),
            "merchantsPerHeadquarters": int(args.seed_merchants_per_hq),
            "seed": args.seed,
        }
        status, text = _http_json(
            "POST",
            endpoints["seed_bootstrap"],
            seed_payload,
            args.timeout,
            headers={"X-API-KEY": operator_api_key},
        )
        if status != 200:
            log(f"[seed:bootstrap] FAIL status={status} body={text}")
            return 2

        data = _safe_json(text) or {}
        merchants = data.get("merchants") or []
        for m in merchants[: args.merchant_pool]:
            if not m.get("id"):
                continue
            merchant_pool.append(
                {
                    "id": m["id"],
                    "merchantCode": m.get("merchantCode", ""),
                    "name": m.get("name", ""),
                    "businessType": m.get("businessType", "OTHER"),
                }
            )

        log(
            f"[seed] bootstrap ok headquarters={len(data.get('headquarters') or [])} merchants={len(merchant_pool)}"
        )

    if args.mode in ["seed-generate", "generate"] and not merchant_pool:
        text = _http_json_or_die(
            "GET",
            endpoints["merchant_list"] + f"?page=0&size={int(args.merchant_pool)}",
            None,
            args.timeout,
        )
        data = _safe_json(text) or {}
        merchants = data.get("content") or []
        for m in merchants[: args.merchant_pool]:
            if not m.get("id"):
                continue
            merchant_pool.append(
                {
                    "id": m["id"],
                    "merchantCode": m.get("merchantCode", ""),
                    "name": m.get("name", ""),
                    "businessType": m.get("businessType", "OTHER"),
                }
            )

        log(f"[generate] merchants loaded={len(merchant_pool)}")

    if not merchant_pool:
        log("[fatal] merchant pool is empty; cannot continue")
        return 2

    if args.mode == "seed":
        log("[done] seed-only")
        return 0

    start_ts = time.time()
    next_report = start_ts
    report_interval = 10.0
    counters = {
        "payments_ok": 0,
        "payments_fail": 0,
        "refunds_ok": 0,
        "refunds_fail": 0,
    }

    try:
        while True:
            if (
                args.duration_seconds > 0
                and (time.time() - start_ts) >= args.duration_seconds
            ):
                break

            m = merchant_pool[rng.randrange(0, len(merchant_pool))]
            pay_payload = build_payment_payload(rng, m["id"], m["merchantCode"])
            desired_status, desired_failure_reason = choose_payment_outcome(rng)
            status, text = _http_json(
                "POST", endpoints["payment_create"], pay_payload, args.timeout
            )
            if status != 201:
                counters["payments_fail"] += 1
                log(
                    f"[payment:create] FAIL status={status} merchant={m['merchantCode']} amount={_format_amount_krw(pay_payload['amount'])} body={text}"
                )
            else:
                counters["payments_ok"] += 1
                pay = _safe_json(text) or {}
                payment_id = pay.get("id")
                if not payment_id:
                    log(f"[payment:create] WARN missing id body={text}")
                else:
                    # Payment lifecycle transitions (Requested -> Processed -> Completed/Failed/Cancelled)
                    if desired_status != "PAYMENT_PENDING":
                        sleep_small_ms()
                        p_status, p_text = _http_json(
                            "POST",
                            endpoints["payment_process"].format(id=payment_id),
                            None,
                            args.timeout,
                        )
                        if p_status != 200:
                            counters["payments_fail"] += 1
                            log(
                                f"[payment:process] FAIL status={p_status} payment={payment_id} body={p_text}"
                            )

                    if desired_status == "PAYMENT_PROCESSING":
                        pass
                    elif desired_status == "PAYMENT_COMPLETED":
                        sleep_small_ms()
                        c_status, c_text = _http_json(
                            "POST",
                            endpoints["payment_complete"].format(id=payment_id),
                            None,
                            args.timeout,
                        )
                        if c_status != 200:
                            counters["payments_fail"] += 1
                            log(
                                f"[payment:complete] FAIL status={c_status} payment={payment_id} body={c_text}"
                            )
                        else:
                            # Refund lifecycle transitions (only for completed payments)
                            if rng.random() < args.refund_rate:
                                refund_payload = build_refund_payload(
                                    rng,
                                    int(pay_payload["amount"]),
                                    m.get("businessType", "OTHER"),
                                )
                                refund_outcome, refund_failure_reason = (
                                    choose_refund_outcome(rng)
                                )
                                refund_url = endpoints["refund_create"].format(
                                    id=payment_id
                                )
                                r_status, r_text = _http_json(
                                    "POST", refund_url, refund_payload, args.timeout
                                )
                                if r_status != 201:
                                    counters["refunds_fail"] += 1
                                    log(
                                        f"[refund:create] FAIL status={r_status} payment={payment_id} refund={_format_amount_krw(refund_payload['refundAmount'])} body={r_text}"
                                    )
                                else:
                                    counters["refunds_ok"] += 1
                                    refund = _safe_json(r_text) or {}
                                    refund_id = refund.get("id")
                                    if not refund_id:
                                        log(
                                            f"[refund:create] WARN missing id body={r_text}"
                                        )
                                    else:
                                        if refund_outcome != "REFUND_PENDING":
                                            sleep_small_ms()
                                            rp_status, rp_text = _http_json(
                                                "POST",
                                                endpoints["refund_process"].format(
                                                    id=refund_id
                                                ),
                                                None,
                                                args.timeout,
                                            )
                                            if rp_status != 200:
                                                counters["refunds_fail"] += 1
                                                log(
                                                    f"[refund:process] FAIL status={rp_status} refund={refund_id} body={rp_text}"
                                                )

                                        if refund_outcome == "REFUND_PROCESSING":
                                            pass
                                        elif refund_outcome == "REFUND_COMPLETED":
                                            sleep_small_ms()
                                            rc_status, rc_text = _http_json(
                                                "POST",
                                                endpoints["refund_complete"].format(
                                                    id=refund_id
                                                ),
                                                None,
                                                args.timeout,
                                            )
                                            if rc_status != 200:
                                                counters["refunds_fail"] += 1
                                                log(
                                                    f"[refund:complete] FAIL status={rc_status} refund={refund_id} body={rc_text}"
                                                )
                                        elif refund_outcome == "REFUND_FAILED":
                                            sleep_small_ms()
                                            rf_status, rf_text = _http_json(
                                                "POST",
                                                endpoints["refund_fail"].format(
                                                    id=refund_id
                                                ),
                                                {
                                                    "failureReason": refund_failure_reason
                                                    or "SYSTEM"
                                                },
                                                args.timeout,
                                            )
                                            if rf_status != 200:
                                                counters["refunds_fail"] += 1
                                                log(
                                                    f"[refund:fail] FAIL status={rf_status} refund={refund_id} body={rf_text}"
                                                )

                    elif desired_status == "PAYMENT_FAILED":
                        sleep_small_ms()
                        f_status, f_text = _http_json(
                            "POST",
                            endpoints["payment_fail"].format(id=payment_id),
                            {"failureReason": desired_failure_reason or "SYSTEM"},
                            args.timeout,
                        )
                        if f_status != 200:
                            counters["payments_fail"] += 1
                            log(
                                f"[payment:fail] FAIL status={f_status} payment={payment_id} body={f_text}"
                            )

                    elif desired_status == "PAYMENT_CANCELLED":
                        sleep_small_ms()
                        x_status, x_text = _http_json(
                            "POST",
                            endpoints["payment_cancel"].format(id=payment_id),
                            None,
                            args.timeout,
                        )
                        if x_status != 200:
                            counters["payments_fail"] += 1
                            log(
                                f"[payment:cancel] FAIL status={x_status} payment={payment_id} body={x_text}"
                            )

            now = time.time()
            if now >= next_report:
                log(
                    "[stats] "
                    + f"payments ok={counters['payments_ok']} fail={counters['payments_fail']} "
                    + f"refunds ok={counters['refunds_ok']} fail={counters['refunds_fail']}"
                )
                next_report = now + report_interval

            sleep_ms = args.sleep_ms + rng.randint(0, max(0, args.jitter_ms))
            time.sleep(sleep_ms / 1000.0)
    except KeyboardInterrupt:
        log("\n[stop] interrupted")

    log(
        "[done] "
        + f"payments ok={counters['payments_ok']} fail={counters['payments_fail']} "
        + f"refunds ok={counters['refunds_ok']} fail={counters['refunds_fail']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
