# GUIDE (Assuming a Production-Like Environment)

This document explains how to prepare StreamSheet test data in a **production-like setup**.

## Goal

- (1) Prepare DB
- (2) Seed initial master data (HQ / merchants / some transactions, etc.)
- (3) Generate transactions continuously, like real inbound traffic
- (4) Verify results via Transactions Export (StreamSheet Excel export)

## 1) Run the Dev Stack (docker compose)

Bring up the development stack with a single command:

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

Required configuration (no defaults; startup fails if missing)

- `JWT_SECRET`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_MONGODB_URI`

You can inject these via environment variables or Docker secrets (mount to `/run/secrets` with file names matching the keys above).

What it does

- Runs Postgres and MongoDB in containers
- Seeds initial data using the `pg-admin` seeder profile (runs once and exits)
- Starts `pg-main` and `pg-admin`
- Runs `traffic` in the background (via `./traffic-generate.sh`) to generate transactions continuously

Access

- pg-admin (UI): `http://localhost:8081`
- pg-main (API): internal to the docker network only (not reachable from the host)

## 2) (Optional) Reseed Initial Data (pg-admin seeder profile)

When you start the stack via docker compose, seeding runs automatically by default.
If you want to reset and reseed data, run the seeder profile locally:

```bash
./gradlew :pg-admin:bootRun --args='--spring.profiles.active=seeder --pgdemo.seeder.reset=true'
```

Notes

- `reset=true` clears transactions/merchants/HQ data and reseeds.
- `admin_user` is configured to be preserved so the default operator admin account remains.

## 3) (Optional) Run Applications Manually (pg-main + pg-admin)

If you prefer running without docker compose, start each module with the normal profile.

Note: `pg-main` / `pg-admin` require the configuration values listed in step 1). If they are missing, the application will fail at startup.

```bash
./gradlew :pg-main:bootRun
```

In another terminal:

```bash
./gradlew :pg-admin:bootRun
```

- pg-main: `http://localhost:8080`
- pg-admin: `http://localhost:8081`

## 4) (Optional) Generate Production-Like Transactions (traffic-generate.sh)

`traffic-generate.sh` continuously generates payment/refund transactions against the pg-main REST API.

If you use docker compose, the `traffic` service runs with default settings automatically.
To run it manually:

```bash
./traffic-generate.sh
```

Option examples

- Limit runtime: `./traffic-generate.sh --duration-seconds 300`
- Control pace: `./traffic-generate.sh --sleep-ms 200 --jitter-ms 300`
- Control refund rate: `./traffic-generate.sh --refund-rate 0.05`
- Merchant pool size: `./traffic-generate.sh --merchant-pool 200`

Notes

- This script only generates **transaction traffic** (it does not create additional HQ/merchants).
- If you already ran the DataSeeder in step 2), generating transactions only is usually closer to a “production inbound traffic” assumption.

## 5) Exports (StreamSheet) Guide

This project’s Export feature uses StreamSheet to produce an **Excel file** of Transactions (payments + refunds).

### 5.1 Login

Open the pg-admin login page:

- URL: `http://localhost:8081/login`

Default login accounts (docker initial data + DataSeeder adjustments)

| Category | Role | Email | Password | Note |
| --- | --- | --- | --- | --- |
| Operator (OPERATOR) | Admin (ADMIN) | `admin@pgdemo.com` | `admin123!` | Docker initial data |
| Operator (OPERATOR) | Manager (MANAGER) | - | - | Not provided by default (create via provisioning) |
| Operator (OPERATOR) | Viewer (VIEWER) | - | - | Not provided by default (provisioning API not supported yet) |
| Headquarters (HEADQUARTERS) | Admin (ADMIN) | `hq_admin@pgdemo.com` | `password123!` | Ensured by DataSeeder |
| Headquarters (HEADQUARTERS) | Manager (MANAGER) | - | - | Not provided by default (create via provisioning) |
| Headquarters (HEADQUARTERS) | Viewer (VIEWER) | - | - | Not provided by default (provisioning API not supported yet) |
| Merchant (MERCHANT) | Admin (ADMIN) | `merchant_admin@pgdemo.com` | `password123!` | Ensured by DataSeeder |
| Merchant (MERCHANT) | Manager (MANAGER) | - | - | Not provided by default (create via provisioning) |
| Merchant (MERCHANT) | Viewer (VIEWER) | - | - | Not provided by default (provisioning API not supported yet) |

Notes

- These accounts are created/adjusted during step 2) seeding (even if they already exist, password/status are normalized).

### 5.2 Request and Download Export (UI)

1) Go to `Exports`

- URL: `http://localhost:8081/admin/exports/payments`

2) Set from/to and HQ/Merchant filters, then click `Request export`

3) When the Job status becomes `COMPLETED`, click `Download` to fetch the Excel file

### 5.3 When Export Is Empty or Fails

- First, confirm transactions are being generated.
  - Check `./traffic-generate.sh` logs for increasing payment/refund ok counters
- If the Export Job status is `FAILED`, check the error summary.
