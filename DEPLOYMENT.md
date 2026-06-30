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
   - PostgreSQL superuser credentials (username/password)
   - Grafana admin credentials (username/password)

2. Deploy:
```bash
export KUBECONFIG=~/path/to/stud.yaml
POSTGRES_USER=<username> POSTGRES_PASSWORD=<password> \
GRAFANA_ADMIN_USER=<username> GRAFANA_ADMIN_PASSWORD=<password> \
make helm-install-aet
```

3. Verify (after namespace is created):
```bash
kubectl get pods -n team-devvopps
kubectl get services -n team-devvopps
```

Expected: postgres-0 and postgres-1 pods (primary + replica with streaming replication)

---

## Prerequisites

- Method 1 (GitHub Actions): Credentials stored in GitHub Secrets 
- Method 2 (Manual): Need `kubectl` and `helm` installed locally




