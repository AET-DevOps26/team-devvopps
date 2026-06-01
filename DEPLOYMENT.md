# Automated Deployment to AET Kubernetes Cluster

## GitHub Actions Setup

The workflow `.github/workflows/deploy-k8s.yml` automatically deploys to the AET cluster when you push to `main`.

### Prerequisites

1. Get your `kubeconfig` file from Rancher: https://rancher.ase.cit.tum.de (Profile → Kubeconfig)
2. Add it as a GitHub secret called `KUBECONFIG`

### Adding Secrets/Variables to GitHub

**Option 1: Web UI**
- Go to Settings → Secrets and variables → Actions
- Click "New repository secret" → Add `KUBECONFIG` with your kubeconfig contents
- (Optional) Click "New repository variable" → Add `K8S_NAMESPACE` = `team-devvopps`

**Option 2: GitHub CLI**
```bash
gh secret set KUBECONFIG < path/to/stud.yaml
gh variable set K8S_NAMESPACE --body "team-devvopps"
```

### Testing the Workflow

1. Push to `main` branch (auto-triggers)
2. Or manually trigger: Actions tab → "Deploy to AET Kubernetes Cluster" → "Run workflow"
3. Check logs in the Actions tab to verify success

### Troubleshooting

**"KUBECONFIG secret not configured"** → Add the secret above

**"cluster reachability check failed"** → Verify kubeconfig is valid and cluster is accessible

**"No nodes available for pod scheduling"** → Check namespace/resources exist on cluster

See the workflow file (`.github/workflows/deploy-k8s.yml`) for full deployment logic.

