#!/usr/bin/env python3
import concurrent.futures
import json
import os
import time
import urllib.request

BASE = os.environ.get("PIXBEN_API_BASE", "https://pixben-backend.onrender.com").rstrip("/")
TIMEOUT = float(os.environ.get("LOAD_TIMEOUT", "20"))
ROUNDS = int(os.environ.get("LOAD_ROUNDS", "3"))
ENDPOINTS = ["/api/health", "/productos"]

def request_once(url):
    start = time.perf_counter()
    status = 0
    error = None
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "PixBenPerfCheck/1.0"})
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            status = response.status
            response.read(2048)
    except Exception as exc:
        error = str(exc)
    return {"ms": (time.perf_counter() - start) * 1000, "status": status, "error": error}

def percentile(values, p):
    if not values:
        return None
    values = sorted(values)
    idx = max(0, min(len(values)-1, int(round((len(values)-1)*p))))
    return values[idx]

def run_endpoint(workers, endpoint):
    jobs=[]
    start=time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        for _ in range(ROUNDS):
            for _ in range(workers):
                jobs.append(pool.submit(request_once, BASE + endpoint))
        results=[j.result() for j in jobs]
    wall=time.perf_counter()-start
    ok=[r for r in results if 200 <= r["status"] < 400]
    bad=[r for r in results if not (200 <= r["status"] < 400)]
    lat=[r["ms"] for r in ok]
    return {
        "users":workers,
        "endpoint":endpoint,
        "requests":len(results),
        "ok":len(ok),
        "errors":len(bad),
        "error_rate_pct":round(len(bad)/len(results)*100,2) if results else 0,
        "p50_ms":round(percentile(lat,.50),1) if lat else None,
        "p95_ms":round(percentile(lat,.95),1) if lat else None,
        "max_ms":round(max(lat),1) if lat else None,
        "rps":round(len(results)/wall,2) if wall else None,
        "sample_errors":[r["error"] or f"HTTP {r['status']}" for r in bad[:3]]
    }

print("Warming up", BASE)
warm = None
for attempt in range(1, 5):
    warm = request_once(BASE + "/api/health")
    print(json.dumps({"warmup_attempt": attempt, "warmup": warm}, ensure_ascii=False))
    if 200 <= warm["status"] < 400:
        break
    time.sleep(3)
summary=[]
for users in (10,25,50):
    for endpoint in ENDPOINTS:
        result=run_endpoint(users,endpoint)
        summary.append(result)
        print(json.dumps(result,ensure_ascii=False))
print("SUMMARY_JSON="+json.dumps(summary,ensure_ascii=False))
# Diagnostic only: availability problems are reported in the output but do not
# hide whether the optimization branch itself compiles correctly.
