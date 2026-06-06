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
   - Database credentials (username/password)

2. Deploy:
```bash
export KUBECONFIG=~/path/to/stud.yaml
POSTGRES_USER=<username> POSTGRES_PASSWORD=<password> make helm-install-aet
```

3. Verify (after namespace is created):
```bash
kubectl get pods -n team-devvopps
kubectl get services -n team-devvopps
```

---

## Prerequisites

- Method 1 (GitHub Actions): No setup needed ✅
- Method 2 (Manual): Need `kubectl` and `helm` installed locally

---

## Troubleshooting

**Namespace doesn't exist:**
→ Ask instructors to create `team-devvopps` namespace in Rancher

**Pods stuck in Pending:**
→ Run: `kubectl describe namespace team-devvopps` to check resources




