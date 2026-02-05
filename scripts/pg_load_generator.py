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

    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            raw = resp.read()
            text = raw.decode("utf-8") if raw else ""
            return resp.status, text
    except urllib.error.HTTPError as e:
        raw = e.read()
        text = raw.decode("utf-8") if raw else ""
        return e.code, text


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

    # Weighted status distribution
    roll = rng.randint(0, 999)
    if roll <= 974:
        status = "PAYMENT_COMPLETED"
        failure_reason = None
    elif roll <= 976:
        status = "PAYMENT_FAILED"
        failure_reason = _rand_choice(
            rng,
            [
                "잔액 부족",
                "카드 한도 초과",
                "통신 오류",
                "결제 거절",
                "유효하지 않은 카드",
            ],
        )
    elif roll <= 991:
        status = "PAYMENT_PROCESSING"
        failure_reason = None
    elif roll <= 995:
        status = "PAYMENT_PENDING"
        failure_reason = None
    else:
        status = "PAYMENT_CANCELLED"
        failure_reason = None

    return {
        "merchantId": merchant_id,
        "orderId": order_id,
        "amount": int(amount),
        "paymentMethod": _rand_choice(rng, payment_methods),
        "status": status,
        "failureReason": failure_reason,
    }


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

    roll = rng.randint(0, 999)
    if roll <= 959:
        status = "REFUND_COMPLETED"
        failure_reason = None
    elif roll <= 964:
        status = "REFUND_FAILED"
        failure_reason = _rand_choice(
            rng,
            [
                "환불 기간 초과",
                "계좌 정보 오류",
                "시스템 오류",
                "환불 불가 상품",
            ],
        )
    elif roll <= 984:
        status = "REFUND_PROCESSING"
        failure_reason = None
    else:
        status = "REFUND_PENDING"
        failure_reason = None

    return {
        "refundAmount": int(refund_amount),
        "refundReason": get_refund_reason(rng, business_type),
        "status": status,
        "failureReason": failure_reason,
    }


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

    rng = random.Random(args.seed)
    base = args.base_url.rstrip("/")

    endpoints = {
        "seed_bootstrap": base + "/api/v1/internal/seed/bootstrap",
        "merchant_list": base + "/api/v1/merchants",
        "payment_create": base + "/api/v1/payments",
        "refund_create": base + "/api/v1/payments/{id}/refund",
    }

    merchant_pool = []  # list of dict: {id, merchantCode, name, businessType}

    def log(msg: str):
        sys.stdout.write(msg + "\n")
        sys.stdout.flush()

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
                    if (
                        rng.random() < args.refund_rate
                        and pay.get("status") == "PAYMENT_COMPLETED"
                    ):
                        refund_payload = build_refund_payload(
                            rng,
                            int(pay_payload["amount"]),
                            m.get("businessType", "OTHER"),
                        )
                        refund_url = endpoints["refund_create"].format(id=payment_id)
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
