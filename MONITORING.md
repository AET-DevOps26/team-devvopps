# Monitoring Setup

This document explains the monitoring stack, dashboards, alerts, and how to access them.

## Architecture

The monitoring stack consists of:
- **Prometheus** — collects metrics from all services via `/actuator/prometheus` endpoint
- **Grafana** — visualizes metrics with pre-built dashboards
- **Alert Rules** — defines thresholds for critical incidents

Dashboards auto-load on startup:
- **Request Metrics** — request rate, latency (p95, p99), error rate by service
- **System Health** — service up/down status, JVM memory/threads, HTTP status distribution, active alerts

Both Docker Compose and Kubernetes deployments are fully synchronized using the same:
- Prometheus configuration and alert rules
- Dashboard JSON files
- Grafana datasource & provisioning configs

## Local Development (Docker Compose)

### Starting Monitoring

```bash
make docker-up
```

Metrics are collected every 15 seconds. Alert rules are evaluated every 15 seconds.

### Accessing Dashboards

- **Grafana:** http://localhost:3001 (login: `admin` / `admin123`)
- **Prometheus:** http://localhost:9090


## Kubernetes Deployment

### Prerequisites

Set your kubeconfig to point to the AET cluster:

```bash
export KUBECONFIG=~/pathtostud.yaml
```

### Accessing Dashboards

Port-forward to expose the services locally:

```bash
# Terminal 1: Grafana
kubectl port-forward -n team-devvopps svc/grafana 3001:3000

# Terminal 2: Prometheus  
kubectl port-forward -n team-devvopps svc/prometheus 9090:9090
```

Then access:
- **Grafana:** http://localhost:3001 (login: `admin` / `admin123`)
- **Prometheus:** http://localhost:9090


## Troubleshooting

### Metrics showing "No Data"

**Cause:** Endpoint hasn't been called yet.  
**Fix:** Generate some traffic (create a roadmap, browse the app) and wait 15 seconds for Prometheus to scrape.

### Port-forward fails: "services not found"

**Cause:** Wrong kubeconfig or namespace.  
**Fix:** Ensure you ran:
```bash
export KUBECONFIG=~/Downloads/stud-6.yaml
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
- UP = 4 means all services (roadmap, user, course, api-gateway) are reachable
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