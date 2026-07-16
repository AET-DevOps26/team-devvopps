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

### Request Metrics Dashboard

**Request Rate by Service (req/sec)**
- Shows how many requests each service handles per second
- Normal: steady line (consistent traffic)
- Problem: sudden drops = service went down; sudden spikes = traffic surge

**Total Requests by Service**
- Cumulative count over the 5-minute window
- Typical: 60-100 total requests (includes health checks, Prometheus scrapes, user activity)
- Normal activity: all services show similar counts

**Request Latency Percentiles (p95, p99)**
- Shows how long requests take (in milliseconds)
- p95 = 95% of requests are faster than this; p99 = 99% of requests are faster than this
- Healthy: < 500ms for p95
- Slow: > 1000ms (alerts will fire)
- No data: endpoint hasn't been called yet

**Error Rate (4xx/5xx) by Service (%)**
- Shows what % of requests failed (4xx = client error, 5xx = server error)
- Healthy: 0% (green)
- Warning: > 5% (yellow — alert will fire)
- Shows which specific service has problems

### System Health Dashboard

**Services UP / Services DOWN**
- UP = 5 means all services (roadmap, user, course, api-gateway, llm) are reachable
- DOWN > 0 = ServiceDown alert is firing (critical)

**Success Rate (%)**
- % of requests that returned 2xx (success)
- Healthy: > 95% (green)
- Warning: 75-95% (yellow)
- Critical: < 75% (red)

**JVM Heap Memory Usage by Service**
- Shows memory consumption per service
- Healthy: steady line, not growing over time
- Problem: constantly rising = memory leak

**JVM Thread Count by Service**
- Active threads in each service
- Healthy: flat line (50-100 threads typically)
- Problem: rapidly increasing = thread exhaustion (too many requests)

**Active Alerts (Prometheus)**
- List of currently firing alerts
- Empty = everything is OK 
- ServiceDown alert = a service crashed or is unreachable 
- HighErrorRate = too many errors (> 5%) 
- HighLatency = requests are slow (p95 > 1s) 

### Auth & Security Dashboard

**Registered Users / Signups**
- Total user count and signup rate over time
- Sudden signup spikes can indicate abuse

**Login Rate by Result / Login Failures (5m)**
- Successful vs. failed logins per second
- A burst of failures for one account or from steady traffic = possible brute-force attempt

**Gateway Rejections by Reason**
- Requests the api-gateway rejected before they reached a service (missing token, expired token, invalid signature, insufficient role)
- A spike in `invalid signature` after a deploy usually means the `JWT_SIGNING_KEY` changed and old sessions are being invalidated

**Auth Events (live)**
- Live tail of auth event logs from user-service (signups, logins, logouts, rejections) for incident triage