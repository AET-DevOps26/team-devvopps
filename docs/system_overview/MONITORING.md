# Monitoring Setup

This document explains the monitoring stack, dashboards, alerts, and how to access them.

## Architecture

The monitoring stack consists of:
- **Prometheus** — collects metrics from all services via `/actuator/prometheus` endpoint
- **Grafana** — visualizes metrics with pre-built dashboards
- **Alert Rules** — defines thresholds for critical incidents
- **Loki** — Log aggregation server (stores and indexes logs)
- **Promtail** — Agent that discovers Docker containers and ships logs to Loki


Dashboards auto-load on startup (exported JSON lives in `helm/team-devvopps/grafana-dashboards/`, shared by the compose and Kubernetes setups):
- **Request Metrics** — request rate, latency (p95, p99), error rate by service
- **System Health** — service up/down status, JVM memory/threads, HTTP status distribution, active alerts
- **Logs Dashboard** — Grafana dashboard for viewing logs. Includes live logs, error counts, and log volume by service
- **LLM Service** — roadmap generation success rate, request outcomes (success / parse_error / provider_error / quota_exceeded), LLM call latency by provider, token consumption, and live error logs
- **Auth & Security** — registered users, signups, login rate by result, login failures, gateway JWT rejections by reason, and live auth event logs

Log format differs per runtime:
- **AET (containerd):** `cri` pipeline stage (default in values.yaml)
- **docker-desktop:** `docker` pipeline stage (set in values-local.yaml)

How Logs Flow:
1. All services log to stdout
2. Promtail detects containers via Docker socket and ships logs to Loki
3. Loki stores logs with `service` label for filtering
4. Grafana visualizes logs via LogQL queries

Logs are kept for **7 days** and automatically deleted after.



## Docker

```bash
make docker-up
```

Metrics are collected every 15 seconds. Alert rules are evaluated every 15 seconds.

### Accessing Dashboards on Docker

- **Grafana:** http://localhost:3001 — login with `GRAFANA_ADMIN_USER` (default `admin`) / `GRAFANA_ADMIN_PASSWORD` from your `infra/.env`
- **Prometheus:** http://localhost:9090

Grafana can also be opened from inside the app: log in as an admin and use the **Grafana ↗** button in the Admin Panel (shown when the `grafanaLink` feature flag is enabled).

> Safari quirk: on localhost the Admin Panel button may appear to do nothing — Safari
> silently blocks cross-port `target="_blank"` links without showing a popup indicator.
> Open http://localhost:3001 directly instead (or use another browser).

> **Note:** Grafana no longer starts with a default password anywhere. Locally, compose fails fast unless `GRAFANA_ADMIN_PASSWORD` is set in `infra/.env` (pick any value — Grafana is only reachable from your machine). On the AET cluster, Grafana is publicly exposed via ingress, so the deploy workflow requires the `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` GitHub Secrets and fails fast if they are missing.

<br>

## Kubernetes AET Deployment

### Prerequisites

Set your kubeconfig to point to the AET cluster:

```bash
export KUBECONFIG=~/path/to/stud.yaml
```

### Logging Stack (Kubernetes-specific)

On Kubernetes, Promtail runs as a **DaemonSet** (one pod per node) and discovers logs from `/var/log/pods` using Kubernetes service discovery, restricted to the `team-devvopps` namespace.

**Important:** Promtail's Kubernetes discovery filters pods by node using the `HOSTNAME` environment variable (injected from `spec.nodeName`). Without this, discovery silently matches zero pods and Loki receives no logs. This is configured in the daemonset template and critical for multi-node clusters.


### Accessing Dashboards

On AET, Grafana is publicly reachable through the ingress:

- **Grafana:** https://team-devvopps.stud.k8s.aet.cit.tum.de/grafana (login: credentials from the `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` GitHub Secrets)

As on Docker, admins can also reach it via the **Grafana ↗** button in the app's Admin Panel.

Prometheus and Loki are not publicly exposed — port-forward to reach them (works for Grafana too):

```bash
# Terminal 1: Grafana (alternative to the ingress URL)
kubectl port-forward -n team-devvopps svc/grafana 3001:3000

# Terminal 2: Prometheus
kubectl port-forward -n team-devvopps svc/prometheus 9090:9090

# Terminal 3: Loki (if needed for API access)
kubectl port-forward -n team-devvopps svc/loki 3100:3100
```

Then access:
- **Grafana:** http://localhost:3001
- **Prometheus:** http://localhost:9090

<br>

## Troubleshooting

### Metrics showing "No Data"

**Cause:** Endpoint hasn't been called yet.  
**Fix:** Generate some traffic (create a roadmap, browse the app) and wait 15 seconds for Prometheus to scrape.

### Port-forward fails: "services not found"

**Cause:** Wrong kubeconfig or namespace.  
**Fix:** Ensure you ran:
```bash
export KUBECONFIG=~/path/to/stud.yaml
```
### To test alerts

Stop one of the services with Docker:
```bash
docker stop infra-roadmap-service-1
```

Then:

- Wait 1+ minute (the alert rule has for: 1m)
- Go to Grafana System Health dashboard
- Look at the bottom "Active Alerts (Prometheus)" panel
- You should see ServiceDown alert firing 

To recover:
```bash
docker start infra-roadmap-service-1
```

<br>

## Understanding the Dashboards (Extra Explanation)

Panel descriptions below are checked against each dashboard's actual PromQL/LogQL
queries (`helm/team-devvopps/grafana-dashboards/*.json`), not just their titles.

### Request Metrics Dashboard

**Request Rate by Service (req/sec)**
- `sum(rate(http_server_requests_seconds_count[5m])) by (job)` — requests/sec per service, averaged over 5 minutes
- Normal: steady line (consistent traffic)
- Problem: sudden drops = service went down; sudden spikes = traffic surge

**Total Requests by Service**
- `sum(http_server_requests_seconds_count) by (job)` — cumulative request count per service since the metric was last reset (service start), not a 5-minute window
- Useful to compare relative traffic volume between services, not as an absolute number

**Request Latency Percentiles (p95, p99)**
- p95/p99 of `http_server_requests_seconds_bucket`, in milliseconds, per service
- p95 = 95% of requests are faster than this; p99 = 99% of requests are faster than this
- The `HighLatency` alert fires when a service's p95 exceeds **1000ms** for 2+ minutes
- No data: endpoint hasn't been called yet in the current window

**Error Rate (4xx/5xx) by Service (%)**
- `rate(...{status=~"[45].."}[5m]) / rate(...[5m]) * 100` per service
- The `HighErrorRate` alert fires when this exceeds **5%** for 2+ minutes
- Shows which specific service has problems

**HTTP Status Code Distribution (5m window)**
- `sum(increase(http_server_requests_seconds_count[5m])) by (status)` — request count per HTTP status code, summed across all services
- Healthy: dominated by `200`; a rising share of `4xx`/`5xx` codes is the same signal as Error Rate above, broken down by status instead of by service

### System Health Dashboard

**Services UP / Services DOWN**
- `count(up{job=~"...five services..."} == 1)` / `== 0` — how many of the 5 backend services (user, course, roadmap, api-gateway, llm) Prometheus can currently scrape
- UP = 5: all services reachable
- DOWN > 0: the `ServiceDown` alert fires after 1 minute of a service being unreachable

**Success Rate (%)**
- `rate(...{outcome="SUCCESS"}[5m]) / rate(...[5m]) * 100` — % of requests Spring classifies as a successful outcome (2xx), across all Spring Boot services
- Not shown for llm-service, which reports outcomes on the separate LLM Service dashboard instead

**JVM Heap Memory Usage (Spring services)**
- Used vs. max heap bytes per Spring Boot service (`jvm_memory_used_bytes` / `jvm_memory_max_bytes`, area="heap")
- Healthy: steady line relative to max, not growing over time
- Problem: constantly rising toward max = possible memory leak

**JVM Thread Count (Spring services)**
- `jvm_threads_live_threads` per Spring Boot service
- Healthy: flat line
- Problem: rapidly increasing = thread exhaustion (too many concurrent requests, or a stuck downstream call)

**Roadmap Generation Success Rate (%)**
- Success rate specifically for `POST /roadmaps/generate` (as measured at roadmap-service, not llm-service)
- A drop here while overall Success Rate looks fine points at roadmap generation specifically — check the LLM Service dashboard next

**Total Courses**
- `database_courses{job="course-service"}` — current row count in `coursedb`
- Confirms the course seeder has run; 0 means roadmap generation has no course catalogue to recommend from

**Roadmap Generation Latency (p95, p99)**
- p95/p99 latency specifically for `POST /roadmaps/generate`, in milliseconds
- Expect this to be much higher than the general Request Latency panel — it includes the full round-trip to the LLM provider

**Active Alerts (Prometheus)**
- Live list of currently firing alerts
- Empty = everything is OK
- `ServiceDown` = a service crashed or is unreachable (fires after 1m)
- `HighErrorRate` = >5% of requests are 4xx/5xx for a service (fires after 2m)
- `HighLatency` = a service's p95 latency is above 1s (fires after 2m)

### Logs Dashboard

**Live Logs (Latest 100 lines)**
- Raw log tail across all services, newest first
- Use to see exactly what a service printed around the time of an issue

**Error Events (live) / Warning Events (live)**
- Live-filtered view of log lines containing `ERROR` / `WARN`
- Empty: no problems currently being logged
- Steady trickle: normal for warnings (e.g. retries); errors should stay rare

**Log Volume by Service (5m avg)**
- Log lines per service, averaged over a 5-minute window
- Normal: steady, low baseline (health checks, routine activity)
- Problem: a sudden spike usually means a service is failing repeatedly and logging the same error on every retry

### LLM Service Dashboard

**LLM Response Validity Rate (5m)**
- `rate(llm_requests_total{status="success"}[5m]) / rate(llm_requests_total[5m]) * 100`
- Healthy: close to 100%; a drop means a growing share of requests are failing or returning unparseable output — see Request Outcomes below for which

**Roadmaps Generated (1h)**
- Count of successful `/recommend` calls in the last hour

**Tokens Consumed (24h)**
- Total tokens used across all providers in the last 24 hours
- Useful for sanity-checking cost and load, not a health signal by itself

**Quota Rejections (1h)**
- Requests rejected in the last hour because a user hit their monthly token limit
- Non-zero is expected behavior (the quota working), not necessarily a problem

**Requests by Provider (24h)**
- Request count in the last 24h, split by provider (Logos / Groq / LM Studio)
- Confirms the `llmUseLogos` feature flag is routing traffic to the expected provider

**Request Outcomes by Status (/s)**
- Requests per second by outcome label on `llm_requests_total`: `success`, `parse_error`, `provider_error`, `quota_exceeded`
- Healthy: almost all `success`
- `parse_error` rising: the model is returning output the service can't parse into a roadmap
- `provider_error` rising: the upstream LLM provider (Groq/Logos/LM Studio) is failing or unreachable

**LLM Call Latency by Provider (p50, p95)**
- How long the upstream model call itself takes, split by provider
- Local (LM Studio) is expected to be much slower than cloud providers — see the README's LM Studio notes
- A rising p95 for one provider suggests that provider is degraded

**Token Burn Rate by Provider/Type (/s)**
- Tokens consumed per second, split by provider and token type (prompt vs. completion)
- Useful for spotting an unusually expensive prompt or a runaway loop

**LLM Service Errors & Warnings (live)**
- Live tail of llm-service's own `ERROR`/`WARN` log lines — same kind of triage as the general Logs dashboard, scoped to this service

### Auth & Security Dashboard

**Registered Users**
- `database_users` — current row count in `userdb`

**Login Failures (5m)**
- Failed logins in the last 5 minutes

**Signups (1h)**
- New signups in the last hour

**Gateway Rejections (5m)**
- Requests the api-gateway rejected in the last 5 minutes before they reached a service

**Login Rate by Result (/s)**
- Successful vs. failed logins per second

**Gateway Rejections by Reason (/s)**
- Gateway rejections per second, split by reason. The gateway only distinguishes two reasons: `unauthorized` (missing/invalid JWT) and `forbidden` (valid JWT, insufficient role)
- A spike in `unauthorized` right after a deploy usually means the `JWT_SIGNING_KEY` changed and existing sessions are being invalidated

**Signups (/s)**
- Signup rate per second (same data as the "Signups (1h)" stat, plotted as a rate)

**Auth Events (live)**
- Live tail of `[Auth]`-tagged log lines from user-service (signups, logins, logouts) for incident triage