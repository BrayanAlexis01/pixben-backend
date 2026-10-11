#!/usr/bin/env python3
"""Read-only bounded load test for PixBen. Does not create orders or modify inventory."""
import asyncio
import json
import os
import statistics
import time
from collections import Counter
from urllib.parse import urljoin
import aiohttp

BASE = os.getenv("PIXBEN_API_BASE", "https://pixben-backend.onrender.com").rstrip("/") + "/"
STAGES = [int(v) for v in os.getenv("STAGES", "100,300,500").split(",") if v]
SECONDS = max(10, int(os.getenv("STAGE_SECONDS", "20")))
THINK_SECONDS = 10.0
TIMEOUT = aiohttp.ClientTimeout(total=15)
PATHS = ("/productos/pagina?pagina=0&limite=16", "/productos/pagina?pagina=0&limite=16", "/productos/pagina?pagina=0&limite=16", "/productos/destacados", "/api/health")

def percentile(values, frac):
    values = sorted(values)
    if not values:
        return None
    return round(values[min(len(values) - 1, int((len(values) - 1) * frac))], 1)

async def run_stage(session, users):
    measured = []
    start_stage = time.monotonic()
    async def virtual_user(user_no):
        # The stagger avoids a synthetic 500-request spike while still simulating
        # hundreds of active visitors navigating across the same 20-second window.
        await asyncio.sleep((user_no % users) / users * THINK_SECONDS)
        path = PATHS[user_no % len(PATHS)]
        while time.monotonic() - start_stage < SECONDS:
            ts = time.monotonic()
            try:
                async with session.get(urljoin(BASE, path.lstrip("/"))) as response:
                    body = await response.read()
                    measured.append((response.status, (time.monotonic()-ts)*1000, len(body), path))
            except (aiohttp.ClientError, asyncio.TimeoutError) as exc:
                measured.append((0, (time.monotonic()-ts)*1000, 0, str(exc)[:100]))
            await asyncio.sleep(THINK_SECONDS)
    await asyncio.gather(*(virtual_user(i) for i in range(users)))
    duration = time.monotonic() - start_stage
    ok = [r[1] for r in measured if 200 <= r[0] < 400]
    errors = [r for r in measured if not (200 <= r[0] < 400)]
    return {
        "active_virtual_users": users,
        "duration_s": round(duration, 1),
        "requests": len(measured),
        "rps": round(len(measured) / duration, 1),
        "errors": len(errors),
        "error_rate_pct": round(100*len(errors)/len(measured), 2) if measured else 100,
        "p50_ms": percentile(ok, .5),
        "p95_ms": percentile(ok, .95),
        "p99_ms": percentile(ok, .99),
        "max_ms": round(max(ok), 1) if ok else None,
        "mb_downloaded": round(sum(r[2] for r in measured) / 1_000_000, 1),
        "statuses": dict(Counter(str(r[0]) for r in measured))
    }

async def main():
    print("Testing read-only optimized catalog and health endpoints on", BASE, flush=True)
    connector = aiohttp.TCPConnector(limit=180, limit_per_host=180)
    async with aiohttp.ClientSession(timeout=TIMEOUT, connector=connector,
                                     headers={"User-Agent": "PixBenBoundedPerformanceProbe/1.0"}) as session:
        async with session.get(urljoin(BASE, "api/health")) as r:
            print("Health warmup HTTP", r.status, flush=True)
            if r.status >= 400:
                raise SystemExit("Backend is not healthy before the load test")
        results = []
        for users in STAGES:
            print(f"START STAGE: {users} virtual users", flush=True)
            result = await run_stage(session, users)
            print("STAGE_RESULT=" + json.dumps(result), flush=True)
            results.append(result)
            if result["error_rate_pct"] > 10 or (result["p95_ms"] or 99999) > 10000:
                print("Stopping early due to errors or latency", flush=True)
                break
        print("SUMMARY_JSON=" + json.dumps(results), flush=True)

if __name__ == "__main__":
    asyncio.run(main())
