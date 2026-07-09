# Deployment to AET Kubernetes Cluster

## For Tutors: Deploy the Project

Choose one method:

### Method 1: Automatic Deployment (Easiest)

**All credentials in GitHub. Just trigger the pipeline:**

1. Go to GitHub repo → Actions tab
2. Click "Deploy to AET Kubernetes Cluster"
3. Click "Run workflow"
4. Check logs for success

Done!

---

### Method 2: Manual Deployment

**If you prefer to deploy from command line:**

1. Contact the team to get:
   - kubeconfig file (stud.yaml)
   - PostgreSQL superuser + replication credentials
   - Grafana admin credentials (username/password)
   - Groq API key

2. Deploy:
```bash
export KUBECONFIG=~/path/to/stud.yaml
POSTGRES_USER=<username> POSTGRES_PASSWORD=<password> \
POSTGRES_REPLICATION_USER=<username> POSTGRES_REPLICATION_PASSWORD=<password> \
GRAFANA_ADMIN_USER=<username> GRAFANA_ADMIN_PASSWORD=<password> \
GROQ_API_KEY=<key> \
make helm-install-aet
```

3. Verify (after namespace is created):
```bash
kubectl get pods -n team-devvopps
kubectl get services -n team-devvopps
```

Expected: postgres-0 and postgres-1 pods (primary + replica with streaming replication)

---

## How Credentials Work (per environment)

| Environment | Where credentials come from | Weak defaults possible? |
|---|---|---|
| **AET via GitHub Actions** | GitHub Secrets, injected with `--set` by the deploy workflow | No — workflow fails fast if secrets are missing |
| **AET manual (`make helm-install-aet`)** | Env vars passed to make, injected with `--set` | No — make fails without them; helm templates also `require` non-empty passwords (`values-aet.yaml` sets them to `""`) |
| **Local Kubernetes (`make k8s-deploy`)** | `helm/team-devvopps/values-local.yaml` (hardcoded local-only credentials, committed) | Yes, and that's fine — local cluster is not reachable from the internet |
| **Local Docker (`make docker-up`)** | Hardcoded in `infra/docker-compose.yml` (`admin`/`admin` etc.) | Yes, fine — localhost only |

---

## Prerequisites

- Method 1 (GitHub Actions): Credentials stored in GitHub Secrets 
- Method 2 (Manual): Need `kubectl` and `helm` installed locally




