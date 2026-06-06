# team-devvopps

## System Architecture

### Microservices
The backend consists of **3 independent Spring Boot microservices** behind an API Gateway:

- **api-gateway** (port 8080): Routes all incoming requests to the appropriate service
- **user-service** (port 8081): User management
- **course-service** (port 8082): Mock TUM course database (source of truth for university courses)
- **roadmap-service** (port 8083): Personalized learning roadmap generation and tracking

Each service has:
- Its own PostgreSQL database (userdb, coursedb, roadmapdb)
- Independent entity models with auto-created schema
- REST API endpoints

**Entity Relationships:** See the UML diagram in `/server/docs/` for complete data model and relationships.

---

## Quick Start

**Choose your setup method:**

- **Local development with your IDE:** [Running Locally (Development)](#running-locally-development)
- **Full stack with containers:** [Running with Docker Compose](#running-with-docker-compose) or [Running with Kubernetes](#running-with-kubernetes)

---

## Running Locally (Development)

For active development without Docker for the services.

### Prerequisites
- **Docker Desktop** (for PostgreSQL only)
- **Miniconda** or **Anaconda**
- **Java 25** (included in conda environment)

### Setup

```bash
# 1. Create conda environment
conda env create -f environment.yml
conda activate team-devvopps

# 2. Start PostgreSQL in Docker
cd server
docker-compose up

# 3. In another terminal, start all Spring Boot services
make dev

# 4. In another terminal, start frontend (optional)
cd client
npm install
npm run dev
```

**Services available at:**
- API Gateway: http://localhost:8080
- user-service: http://localhost:8081
- course-service: http://localhost:8082
- roadmap-service: http://localhost:8083
- Frontend: http://localhost:3000

### Viewing Logs

```bash
tail -f logs/user-service.log
tail -f logs/api-gateway.log
```

### Populate Course Database (First Time)

```bash
cd server/course-service
python3 fetch_and_seed_courses.py   # macOS/Linux
python fetch_and_seed_courses.py    # Windows
```

### Stop & Cleanup

```bash
# Stop services
make dev-stop

# Reset database
cd server
docker-compose down
docker volume rm server_postgres_data
```

---

## Running with Docker Compose

Runs the full stack with a single command. No Kubernetes or local Java installation required.

### Prerequisites
- **Docker Desktop** (must be open and running)

### Commands

```bash
make docker-up      # Start full stack
make docker-down    # Stop stack
make docker-reset   # Stop and delete all data
```

### Access
- **React Client:** http://localhost:3000
- **API Gateway:** http://localhost:8080

---

## Running with Kubernetes

The fastest containerized way to run the entire stack.

### Prerequisites
- **Docker Desktop** with **Kubernetes enabled**
  - Settings → Kubernetes → Enable Kubernetes → Apply & Restart
- **Helm 3.x** (install from https://helm.sh/docs/intro/install/)

### Local Development (Docker Desktop)

**First time setup:**

1. Copy the example secrets file:
```bash
cp helm/team-devvopps/values-secrets.example.yaml helm/team-devvopps/values-secrets.yaml
```

2. Edit `helm/team-devvopps/values-secrets.yaml` with your database credentials:
```yaml
postgres:
  credentials:
    username: your_username
    password: your_password
```

3. Deploy:
```bash
make helm-install
```

**Access:**
- React Client: http://localhost:30000
- API Gateway: http://localhost:30080

**Manage deployment:**
```bash
make helm-upgrade    # Update existing deployment
make helm-delete     # Remove deployment
make k8s-status      # Check pod status
```

### AET Kubernetes Cluster

For deployment to the AET cluster, see [DEPLOYMENT.md](DEPLOYMENT.md)

```bash
make helm-install-aet    # Deploy to AET (uses GitHub Secrets)
```

### Legacy kubectl (Not Recommended)

```bash
make k8s-build        # Build all Docker images
make k8s-deploy       # Deploy to Kubernetes
make k8s-status       # Check status
make k8s-down         # Tear down
```

---

## Deployment Infrastructure

### Azure VM (Staging/Demo)

The application is automatically built and deployed to an Azure VM on every merge to main.

**URLs:**
- **Frontend:** https://client.9.223.113.24.nip.io
- **API Gateway:** https://api.9.223.113.24.nip.io

**How it works:**

1. **Provisioning (one-time):**
   - GitHub Actions → Provision Azure VM → Run workflow
   - Terraform creates VM + Ansible configures Docker

2. **Deployment (automatic on merge to main):**
   - Build job: Creates 5 Docker images → pushes to ghcr.io
   - Deploy job: Copies docker-compose to VM → starts services with Traefik for HTTPS

**Stack on VM:**
- Traefik (reverse proxy + Let's Encrypt HTTPS)
- Postgres 16
- All microservices

**Setup Details (Reference Only):**
- Azure Service Principal: For Terraform to provision VM
- Terraform State Backend: Shared storage for team state
- SSH Key Pair: For accessing VM
- All configured in GitHub Secrets

See [README.md section on Azure](README.md#azure-vm-stagingdemo) for detailed setup.

### AET Kubernetes Cluster (Production)

For deployment to the AET Kubernetes cluster used in the course:

- See [DEPLOYMENT.md](DEPLOYMENT.md) for full instructions
- Two deployment options: GitHub Actions (automatic) or manual Helm command
- Supports multiple environments with Helm values files

---

## Required GitHub Secrets/Variables

### For Azure VM
| Name | Type | Description |
|---|---|---|
| `AZURE_PUBLIC_IP` | variable | Public IP of Azure VM |
| `AZURE_USER` | variable | SSH username (`azureuser`) |
| `AZURE_PRIVATE_KEY` | secret | SSH private key |
| `ARM_CLIENT_ID` | secret | Azure service principal client ID |
| `ARM_CLIENT_SECRET` | secret | Azure service principal client secret |
| `ARM_SUBSCRIPTION_ID` | secret | Azure subscription ID |
| `ARM_TENANT_ID` | secret | Azure tenant ID |
| `PAT_TOKEN` | secret | GitHub PAT for updating AZURE_PUBLIC_IP |

### For AET Kubernetes
| Name | Type | Description |
|---|---|---|
| `KUBECONFIG` | secret | Kubeconfig for AET cluster access |
| `POSTGRES_USER` | secret | Database username |
| `POSTGRES_PASSWORD` | secret | Database password |
| `K8S_NAMESPACE` | variable | Kubernetes namespace (`team-devvopps`) |

---

## Making Changes

- **Code changes:** Commit to feature branches, open PR, merge to main
- **Linting:** Automatically runs on PR
- **Docker images:** Automatically built and pushed to ghcr.io on merge
- **Deployments:** Automatic to Azure VM, manual/automatic to AET cluster
