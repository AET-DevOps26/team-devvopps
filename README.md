# team-devvopps

TUMgoal is a DevOps course project for helping TUM Computer Science students turn academic or career goals into structured learning roadmaps. Students enter a goal, choose whether it is a career goal or university course goal, and receive milestones, tasks, course recommendations, and progress tracking.

## Project Deliverables

This repository currently includes:

- Local, Docker Compose, Kubernetes, Helm, Azure VM, and AET cluster setup instructions.
- Architecture documentation for the React client, API gateway, Spring Boot microservices, PostgreSQL databases, and GenAI integration.
- OpenAPI specifications for the service APIs in `api/`.
- CI/CD pipelines for linting, image builds, Azure VM deployment, AET Kubernetes deployment, and Azure VM provisioning.
- Operational instructions for logs, Kubernetes status checks, Helm release checks, and deployment troubleshooting.
- A student responsibilities section explaining how team members should contribute, test, document, and operate the project.

## System Architecture

The application uses a microservice-based client-server architecture.

### Components
The backend consists of **3 independent Spring Boot microservices and 1 Python service** behind an API Gateway:

- **api-gateway** (port 8080): Routes all incoming requests to the appropriate service
- **user-service** (port 8081): User management
- **course-service** (port 8082): Mock TUM course database (source of truth for university courses)
- **roadmap-service** (port 8083): Personalized learning roadmap generation and tracking
- **llm-service** (port 8084): Responsible for all AI-driven logic 

Each service has:
- Its own PostgreSQL database (userdb, coursedb, roadmapdb)
- Independent entity models with auto-created schema
- REST API endpoints

Architecture diagrams and product context are in `docs/system_overview/`, including:

- `docs/system_overview/problem_statement.md`
- `docs/system_overview/initial_system_structure.md`
- `docs/system_overview/first_product_backlog.md`
- `docs/system_overview/diagrams/system_architecture.png`

## Repository Structure

```text
.
├── .github/workflows/      # GitHub Actions CI/CD pipelines
├── ansible/                # Azure VM configuration
├── api/                    # OpenAPI service specifications
├── client/                 # React frontend
├── docs/                   # Product docs, diagrams, backlog, UI mockup
├── helm/team-devvopps/     # Helm chart for Kubernetes deployments
├── infra/
│   ├── docker-compose.yml  # Local full-stack Docker Compose setup
│   └── k8s/                # Raw Kubernetes manifests
├── server/                 # Spring Boot backend services
│   ├── api-gateway/
│   │   └── Dockerfile
│   ├── user-service/
│   │   └── Dockerfile
│   ├── course-service/
│   │   └── Dockerfile
│   ├── roadmap-service/
│   │   └── Dockerfile
│   ├── llm-service/        # GenAI/LLM service
│   │   └── Dockerfile
│   ├── compose.yaml        # Backend-only PostgreSQL setup for local development
│   ├── build.gradle        # Shared Gradle build configuration
│   ├── init-databases.sql  # Creates userdb, coursedb, and roadmapdb
│   └── settings.gradle     # Gradle multi-project settings
├── terraform/              # Azure infrastructure provisioning
├── compose.azure.yml       # Azure VM Docker Compose deployment
├── DEPLOYMENT.md           # AET deployment instructions
├── environment.yml         # Conda environment for local development
├── Makefile                # Common local/deployment commands
├── redocly.yaml            # Redocly OpenAPI lint configuration
└── README.md
```

---

## Quick Start

**Choose your setup method:**

- **Local development with your IDE:** [Running Locally (Development)](#running-locally-development)
- **Full stack with containers:** [Running with Docker Compose](#running-with-docker-compose) or [Running with Kubernetes](#running-with-kubernetes)
- **Cloud** (staging/demo): [Azure VM](#azure-vm)

---

## Setup Instructions

### Running Locally (Development)

Use this path when actively developing the services without running every app in containers.

Prerequisites:
- **Docker Desktop** (for PostgreSQL only)
- **Miniconda** or **Anaconda**
- **Java 25** (included in conda environment)

```bash
conda env create -f environment.yml
conda activate team-devvopps

cd server
docker-compose up
```

In another terminal:

```bash
make dev
```

In another terminal, start the frontend:

```bash
cd client
npm install
npm run dev
```

Local URLs:
- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- User-service: http://localhost:8081
- Course-service: http://localhost:8082
- Roadmap-service: http://localhost:8083
- Llm-service: http://localhost:8084

To seed course data for the first time:

```bash
cd server/course-service
python3 fetch_and_seed_courses.py   # macOS/Linux
python fetch_and_seed_courses.py    # Windows
```

To stop local Spring Boot services:

```bash
make dev-stop
```

And to reset the database:
```bash
cd server
docker-compose down
docker volume rm server_postgres_data
```

---

### Running with Docker Compose

Runs the full stack with a single command. No Kubernetes or local Java installation required.

Prerequisites:
- **Docker Desktop** (must be open and running)

Commands:

```bash
make docker-up      # Start full stack
make docker-down    # Stop stack
```

To stop the stack and delete all data:
```bash
cd infra && docker-compose down -v
```

Access:
- **React Client:** http://localhost:3000
- **API Gateway:** http://localhost:8080

---

### Running with Kubernetes

#### Switching kubectl Contexts

`kubectl` and `helm` always operate on the currently active context — check it before deploying anything:

```bash
kubectl config get-contexts                   # List all contexts (* = active)
kubectl config use-context docker-desktop     # Local Kubernetes (Docker Desktop)
kubectl config use-context stud               # AET cluster (server)
```

> ⚠️ `make k8s-deploy` refuses to run unless the context is `docker-desktop`,
> so locally built images can never be deployed to the AET cluster by accident.

#### Local Kubernetes (Recommended)

Deploys the **same Helm chart used on the AET cluster** with local overrides
(`helm/team-devvopps/values-local.yaml`: locally built images, NodePort instead of ingress, single postgres).

Prerequisites:
- **Docker Desktop** with **Kubernetes enabled**
  - Settings → Kubernetes → Enable Kubernetes → Apply & Restart
- **Helm 3.x** (install from https://helm.sh/docs/intro/install/)
- `infra/.env` containing `GROQ_API_KEY=...` (git-ignored; ask a teammate for the key)

Deploy:

```bash
kubectl config use-context docker-desktop
make k8s-deploy       # Builds all images, installs/upgrades the Helm release, restarts pods
```

Access:
- React Client: http://localhost:30000
- API Gateway: http://localhost:30080

Manage deployment:
```bash
make k8s-status      # Check pod status
make k8s-seed        # Re-run the course seeder
make k8s-down        # Tear down (deletes the namespace and all local data)
```

#### AET Kubernetes Cluster

Deployed via GitHub Actions: run **Build Docker Images**, then **Deploy to AET Kubernetes Cluster**.
The workflow connects to the cluster with its own credentials, so your local kubectl context does not matter for this path. For details see [DEPLOYMENT.md](DEPLOYMENT.md).

To inspect the server or deploy manually, switch your context to the AET cluster first:

```bash
kubectl config use-context stud

kubectl get pods -n team-devvopps       # Inspect what is running on the server
make helm-install-aet                   # Manual deploy fallback (requires credentials)
```

> ⚠️ Remember to switch back with `kubectl config use-context docker-desktop`
> before working locally again.

#### Old Plain-YAML Manifests (Fallback Only)

The original hand-written manifests in `infra/k8s/` are kept as a fallback.
Do **not** mix with the Helm-based deploy — run `make k8s-down` before switching methods.

```bash
make k8s-deploy-old   # Build images and deploy using infra/k8s/ manifests
```

### Azure VM (Cloud)

> ⚠️ The VM is stopped by default to preserve free credits. Always stop it again after use.

Deployed via GitHub Actions: 
1. Run **Start VM (Azure)**.
2. Run **Build Docker Images**, then **Deploy to VM**. 
3. Run **Stop VM (Azure)** when done.

The application is available under: https://client.20.240.141.213.nip.io (*If the URL is not working, check whether the public IP has changed in GitHub variables.*)

See [README.md section on Azure](README.md#azure-vm-stagingdemo) for more details.

---

## API Documentation

### OpenAPI
OpenAPI specifications are maintained in `api/`:

- `api/user-service.yaml`
- `api/course-service.yaml`
- `api/roadmap-service.yaml`
- `api/llm-service.yaml`

OpenAPI linting runs in CI with Redocly:

```bash
redocly lint api/user-service.yaml
redocly lint api/course-service.yaml
redocly lint api/roadmap-service.yaml
redocly lint api/llm-service.yaml
```
### Swagger UI
Swagger UI is available, for local development, at `/swagger-ui.html` on each service:

| Service | Local URL |
|---|---|
| User Service | http://localhost:8081/swagger-ui.html |
| Course Service | http://localhost:8082/swagger-ui.html |
| Roadmap Service | http://localhost:8083/swagger-ui.html |
| LLM Service | http://localhost:8084/swagger-ui.html |

### API Gateway
The API Gateway exposes the current implemented service routes:

| Gateway Route | Target Service | Notes |
|---|---|---|
| `/users/**` | `user-service` | Create and fetch users |
| `/courses/**` | `course-service` | List courses, fetch course by ID, search by title |
| `/roadmaps/**` | `roadmap-service` | Generate and retrieve roadmaps |

### Service Discovery
Inter-service communication uses DNS-based service discovery, i.e., no hardcoded IPs or ports.

- Docker Compose: Docker's internal DNS resolves service names. Hostnames are set via environment variables (`USER_SERVICE_HOST`, `LLM_SERVICE_HOST` etc.) in `infra/docker-compose.yml`. Ports are externalised via `USER_SERVICE_PORT`, `LLM_SERVICE_PORT` etc. and centralised in `infra/docker-compose.yml`.
- Kubernetes: Cluster-internal DNS resolves service names within the namespace. All ports are centralised in `helm/team-devvopps/values.yaml` under `services.*` and injected into each pod as environment variables via Helm templates (e.g., `http://course-service:{{ .Values.services.courseService.port }}`).

All service URLs are externalized as environment variables and centralized in:
- `infra/docker-compose.yml` for local Docker development
- `helm/team-devvopps/values.yaml` for Kubernetes deployments

### Endpoints
Current implemented endpoints include:

- `POST /users`
- `GET /users`
- `GET /users/{id}`
- `GET /courses`
- `GET /courses/{id}`
- `GET /courses/search?title=<title>`
- `POST /roadmaps/generate?userId=<id>&goal=<goal>`
- `GET /roadmaps/{id}`

## CI/CD

GitHub Actions workflows are defined in `.github/workflows/`.

| Workflow | Trigger | Purpose |
|---|---|---|
| `lint.yml` | Pull requests to `main`, pushes to non-`main` branches | Runs frontend ESLint, Java checks, actionlint, Helm lint, and OpenAPI lint |
| `build.yml` | Push to `main`, manual dispatch | Builds Docker images for all services and pushes them to GHRC |
| `deploy-vm.yml` | Automatically after `build.yml` completes successfully on `main` (via `workflow_run`) | Temporarily opens SSH access for the runner's IP, deploys the latest images to the Azure VM with Docker Compose, then closes SSH access again |
| `deploy-k8s.yml` | Push to `main`, manual dispatch | Deploys the Helm chart to the AET Kubernetes cluster |
| `provision.yml` | Manual dispatch | Provisions or imports Azure resources with Terraform, temporarily opens SSH for the runner's IP, configures the VM with Ansible, and updates the Azure public IP GitHub variable, then closes SSH access again |
| `start-vm.yml` | Manual dispatch | Starts the Azure VM |
| `stop-vm.yml` | Manual dispatch | Deallocates the Azure VM to stop billing 

**Required GitHub configuration:**

For Azure VM:

| Name | Type | Description |
|---|---|---|
| `AZURE_PUBLIC_IP` | variable | Public IP of Azure VM |
| `AZURE_USER` | variable | SSH username (`azureuser`) |
| `AZURE_PRIVATE_KEY` | secret | SSH private key |
| `ARM_CLIENT_ID` | secret | Azure service principal client ID *(has Network Contributor role on the NSG to manage the temporary CI SSH rule)* |
| `ARM_CLIENT_SECRET` | secret | Azure service principal client secret |
| `ARM_SUBSCRIPTION_ID` | secret | Azure subscription ID |
| `ARM_TENANT_ID` | secret | Azure tenant ID |
| `PAT_TOKEN` | secret | GitHub PAT for updating AZURE_PUBLIC_IP |

For AET Kubernetes:

| Name | Type | Description |
|---|---|---|
| `KUBECONFIG` | secret | Kubeconfig for AET cluster access |
| `POSTGRES_USER` | secret | Database username |
| `POSTGRES_PASSWORD` | secret | Database password |
| `POSTGRES_REPLICATION_USER` | secret | Replication username |
| `POSTGRES_REPLICATION_PASSWORD` | secret | Replication password |
| `GRAFANA_ADMIN_USER` | secret | Grafana admin username |
| `GRAFANA_ADMIN_PASSWORD` | secret | Grafana admin password |
| `GROQ_API_KEY` | secret | Groq API key for llm-service |
| `LOGOS_API_KEY` | secret | Logos API key for llm-service (optional) |
| `K8S_NAMESPACE` | variable | Kubernetes namespace (`team-devvopps`) |

### Azure VM (Staging/Demo)

**How it works:**

1. **Provisioning (one-time):**
   - GitHub Actions → Provision Azure VM → Run workflow
   - Terraform creates VM + Ansible configures Docker

2. **Deployment (automatic on merge to main):**
   - `build.yml`: Creates 5 Docker images → pushes to ghcr.io
   - `deploy-vm.yml`: Temporarily allows the runner's IP through the VM's NSG → Copies docker-compose to VM → starts services with Traefik for HTTPS

**Stack on VM:**
- Traefik (reverse proxy + Let's Encrypt HTTPS)
- Postgres 16
- All microservices

**Setup Details (Reference Only):**
- Azure Service Principal: For Terraform to provision VM
- Terraform State Backend: Shared storage for team state
- SSH Key Pair: For accessing VM
- All configured in GitHub Secrets

**Network Security:**
- SSH (port 22) on the Azure VM's Network Security Group is restricted to the MWN (Münchner Wissenschaftsnetz) range `129.187.0.0/16`, which covers TUM, LMU, BADW, and LRZ.
- To SSH into the VM, connect to TUM's eduVPN first.
- The allowed range is set via the `allowed_ssh_eduVPN` Terraform variable in `terraform/` and defaults to `129.187.0.0/16`. Update it there (and re-run `terraform apply` or the `provision.yml` workflow) if the MWN allocation changes.
- HTTP (80) and HTTPS (443) remain open to all sources, since the application's frontend/API are intended to be publicly available. Only SSH is access-restricted.
- **CI/CD SSH access**: Since the GitHub Actions runners don't have IPs within the MWN range, both `provision.yml` (which runs the Ansible playbook to configure the VM) and `deploy-vm.yml` (which deploys updated images) add a `/32` NSG rule for the runner's own public IP just before connecting over SSH, then delete that rule immediately afterward (even on failure, via `if: always()`). This keeps the NSG closed to the public internet while still allowing automated provisioning and deploys.


### AET Kubernetes Cluster (Production)

For deployment to the AET Kubernetes cluster used in the course:

- See [DEPLOYMENT.md](DEPLOYMENT.md) for full instructions
- Two deployment options: GitHub Actions (automatic) or manual Helm command
- **PostgreSQL High Availability:** postgres-0 (primary) + postgres-1 (replica) with streaming replication
  - Replicas stay in sync automatically
  - On primary failure, Kubernetes restarts it and replication resumes
- Supports multiple environments with Helm values files

## Monitoring And Operations

### Local Logs

`make dev` writes Spring Boot logs to `logs/`:

### For AET Kubernetes
| Name | Type | Description |
|---|---|---|
| `KUBECONFIG` | secret | Kubeconfig for AET cluster access |
| `POSTGRES_USER` | secret | PostgreSQL superuser name (typically "postgres") |
| `POSTGRES_PASSWORD` | secret | PostgreSQL superuser password |
| `POSTGRES_REPLICATION_USER` | secret | Replication username |
| `POSTGRES_REPLICATION_PASSWORD` | secret | Replication password |
| `GRAFANA_ADMIN_USER` | secret | Grafana admin username |
| `GRAFANA_ADMIN_PASSWORD` | secret | Grafana admin password |
| `GROQ_API_KEY` | secret | Groq API key for llm-service |
| `LOGOS_API_KEY` | secret | Logos API key for llm-service (optional) |
| `K8S_NAMESPACE` | variable | Kubernetes namespace (`team-devvopps`) |

## Student Responsibilities

Every student contributor is responsible for keeping the project runnable, documented, and reviewable.

- Work on feature branches and open pull requests into `main`.
- Keep changes small enough to review and describe the tested behavior in the PR.
- Run relevant local checks before requesting review.
- Update OpenAPI specs in `api/` whenever service endpoints or request/response shapes change.
- Update this README or `DEPLOYMENT.md` whenever setup, deployment, ports, secrets, or operational commands change.
- Keep Docker, Kubernetes, Helm, Terraform, and Ansible files aligned with the services they deploy.
- Do not commit real credentials, kubeconfig files, private keys, or production secrets.
- Monitor GitHub Actions after pushing or merging and fix failing pipelines quickly.

## Making Changes

- **Code changes:** Commit to feature branches, open PR, merge to main
- **Linting:** Automatically runs on PR
- **Docker images:** Automatically built and pushed to ghcr.io on merge
- **Deployments:** Automatic to Azure VM, manual/automatic to AET cluster
