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


def _safe_json(text: str):
    if not text:
        return None
    try:
        return json.loads(text)
    except Exception:
        return None


def _format_amount_krw(amount: int) -> str:
    return f"{amount:,}"


def build_merchant_payload(rng: random.Random, merchant_code: str):
    business_types = [
        "CAFE",
        "RESTAURANT",
        "FAST_FOOD",
        "CONVENIENCE_STORE",
        "RETAIL",
        "OTHER",
    ]
    store_types = ["FRANCHISE", "DIRECT"]

    brand_prefixes = [
        "Seoul",
        "Han",
        "River",
        "Blue",
        "Green",
        "Sun",
        "Moon",
        "Prime",
        "Metro",
        "Daily",
        "Lucky",
        "Urban",
        "Breeze",
        "Oak",
        "Pine",
        "Stone",
        "Maple",
        "Cedar",
    ]
    brand_suffixes = [
        "Mart",
        "Cafe",
        "Kitchen",
        "Deli",
        "Bistro",
        "Market",
        "Store",
        "Express",
        "Corner",
        "Foods",
    ]
    districts = [
        "Jongno",
        "Gangnam",
        "Mapo",
        "Yongsan",
        "Songpa",
        "Seocho",
        "Yeongdeungpo",
        "Seongdong",
    ]

    brand = f"{_rand_choice(rng, brand_prefixes)} {_rand_choice(rng, brand_suffixes)}"
    location = _rand_choice(rng, districts)
    name = f"{brand} ({location})"

    start = dt.date.today() - dt.timedelta(days=rng.randint(0, 365 * 2))
    end = start + dt.timedelta(days=rng.randint(30, 365 * 3))

    return {
        "merchantCode": merchant_code,
        "name": name,
        "storeType": _rand_choice(rng, store_types),
        "businessType": _rand_choice(rng, business_types),
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

    order_id = f"ORD-{merchant_code}-{dt.date.today().strftime('%Y%m%d')}-{_now_ms()}-{rng.randint(1000, 9999)}"
    return {
        "merchantId": merchant_id,
        "orderId": order_id,
        "amount": int(amount),
        "paymentMethod": _rand_choice(rng, payment_methods),
    }


def build_refund_payload(rng: random.Random, payment_amount: int):
    reasons = [
        "Customer changed mind",
        "Duplicate payment",
        "Item out of stock",
        "Delivery delay",
        "Service cancellation",
        "Price adjustment",
    ]
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
        "refundReason": _rand_choice(rng, reasons),
    }


def main(argv):
    p = argparse.ArgumentParser(
        description="PG demo load generator: continuously create merchants/payments/refunds via pg-main REST APIs"
    )
    p.add_argument(
        "--base-url", default="http://localhost:8080", help="pg-main base URL"
    )
    p.add_argument("--timeout", type=float, default=5.0, help="HTTP timeout seconds")
    p.add_argument("--seed", type=int, default=None, help="random seed")

    p.add_argument(
        "--merchant-pool",
        type=int,
        default=50,
        help="number of merchants to keep in memory",
    )
    p.add_argument(
        "--bootstrap-merchants",
        type=int,
        default=50,
        help="number of merchants to create at start",
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
        "merchant_create": base + "/api/v1/merchants",
        "payment_create": base + "/api/v1/payments",
        "refund_create": base + "/api/v1/payments/{id}/refund",
    }

    merchant_pool = []  # list of dict: {id, merchantCode, name}

    def log(msg: str):
        sys.stdout.write(msg + "\n")
        sys.stdout.flush()

    if args.dry_run:
        sample_code = (
            f"M-{dt.date.today().strftime('%y%m')}-{rng.randint(100000, 999999)}"
        )
        m = build_merchant_payload(rng, sample_code)
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
            + json.dumps(build_refund_payload(rng, 15000), ensure_ascii=True)
        )
        return 0

    # Bootstrap merchants
    created = 0
    for _ in range(max(0, args.bootstrap_merchants)):
        merchant_code = (
            f"M-{dt.date.today().strftime('%y%m')}-{rng.randint(100000, 999999)}"
        )
        payload = build_merchant_payload(rng, merchant_code)
        status, text = _http_json(
            "POST", endpoints["merchant_create"], payload, args.timeout
        )
        if status != 201:
            log(f"[merchant:create] FAIL status={status} body={text}")
            continue
        data = _safe_json(text)
        if not data or "id" not in data:
            log(f"[merchant:create] FAIL invalid json body={text}")
            continue
        merchant_pool.append(
            {
                "id": data["id"],
                "merchantCode": data.get("merchantCode", merchant_code),
                "name": data.get("name", ""),
            }
        )
        created += 1
        if len(merchant_pool) >= args.merchant_pool:
            break

    log(f"[bootstrap] merchants created={created} pool={len(merchant_pool)}")
    if not merchant_pool:
        log("[fatal] merchant pool is empty; cannot continue")
        return 2

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
                    if rng.random() < args.refund_rate:
                        refund_payload = build_refund_payload(
                            rng, int(pay_payload["amount"])
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
