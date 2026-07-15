# team-devvopps

TUMgoal is a DevOps course project for helping TUM Computer Science students turn academic or career goals into structured learning roadmaps. Students enter a goal, choose whether it is a career goal or university course goal, and receive milestones, tasks, course recommendations, and progress tracking.

## Project Deliverables

This repository currently includes:

- Local, Docker Compose, Kubernetes, Helm, Azure VM, and AET cluster setup instructions.
- Architecture documentation for the React client, API gateway, Spring Boot microservices, PostgreSQL databases, and GenAI integration.
- OpenAPI specifications for the service APIs in `api/`.
- CI/CD pipelines for linting, image builds, Azure VM deployment, AET Kubernetes deployment, and Azure VM provisioning.
- A monitoring stack (Prometheus, Grafana, Loki/Promtail) with exported dashboards and alert rules, documented in [MONITORING.md](MONITORING.md).
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
├── client/                 # React frontend (Dockerfile inside)
├── docs/                   # Product docs, diagrams, backlog, known issues, security notes
├── helm/team-devvopps/     # Helm chart used for ALL Kubernetes deployments (local + AET)
│   ├── grafana-dashboards/ # Exported Grafana dashboard JSON (also mounted by compose)
│   ├── templates/          # Manifests: services, postgres, prometheus, grafana, loki, promtail
│   ├── values.yaml         # Defaults (ports, images, monitoring)
│   ├── values-local.yaml   # Local docker-desktop overrides (NodePort, single postgres)
│   └── values-aet.yaml     # AET cluster overrides (ingress, HA postgres)
├── infra/
│   ├── docker-compose.yml  # Local full-stack Docker Compose setup
│   ├── prometheus.yml      # Prometheus scrape config (compose)
│   ├── prometheus/         # Prometheus alert rules (compose)
│   ├── grafana/            # Grafana provisioning: datasources + dashboard loader (compose)
│   ├── loki/               # Loki config (compose)
│   └── promtail/           # Promtail config (compose)
├── server/                 # Backend services (each service has its own Dockerfile)
│   ├── api-gateway/        # Spring Boot: routing + JWT verification
│   ├── user-service/       # Spring Boot: users, auth, feature flags, settings
│   ├── course-service/     # Spring Boot: TUM course catalogue
│   ├── roadmap-service/    # Spring Boot: roadmap generation and tracking
│   ├── llm-service/        # GenAI service (Python FastAPI)
│   ├── compose.yaml        # Backend-only PostgreSQL setup for local development
│   ├── build.gradle        # Shared Gradle build configuration
│   ├── init-databases.sql  # Creates userdb, coursedb, and roadmapdb
│   └── settings.gradle     # Gradle multi-project settings
├── terraform/              # Azure infrastructure provisioning
├── compose.azure.yml       # Azure VM Docker Compose deployment
├── MONITORING.md           # Monitoring guide: dashboards, alerts, log pipeline
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

In another terminal, start the four Spring Boot services (api-gateway, user-service, course-service, roadmap-service):

```bash
make dev
```

In another terminal, start the Python llm-service (roadmap-service expects it on port 8084):

```bash
cd server/llm-service
pip install -r requirements.txt
PORT=8084 GROQ_API_KEY=<your-key> python main.py
```

> The llm-service needs an LLM provider key: set `GROQ_API_KEY` (Groq cloud) or
> `LOGOS_API_KEY` (TUM-hosted Logos, needs eduVPN off-campus). With neither set it
> falls back to a local LM Studio server on `localhost:1234`. Ask a teammate for a key.
>
> Auth works out of the box in local dev: the services fall back to a committed
> dev-only JWT signing key, so no `JWT_SIGNING_KEY` needs to be configured.

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

Runs the full stack (all services + PostgreSQL + the complete monitoring stack) with a single command. No Kubernetes or local Java installation required.

Prerequisites:
- **Docker Desktop** (must be open and running)
- A git-ignored `infra/.env` file with the variables below

#### Configuration variables (`infra/.env`)

| Variable | Required | Purpose |
|---|---|---|
| `POSTGRES_PASSWORD` | **yes** | Password for the PostgreSQL instance. Pick any value for local dev — compose refuses to start without it (no default/hardcoded password). |
| `GRAFANA_ADMIN_PASSWORD` | **yes** | Grafana admin login password. Same rule: compose fails fast if unset. |
| `GRAFANA_ADMIN_USER` | no (default `admin`) | Grafana admin username. |
| `GROQ_API_KEY` | for roadmap generation | Groq API key used by the llm-service. Ask a teammate for the key. |
| `LOGOS_API_KEY` | alternative to Groq | TUM-hosted Logos key (preferred over Groq when set and the `llmUseLogos` feature flag is on; needs eduVPN off-campus). Without either key, the llm-service falls back to a local LM Studio server. |
| `JWT_SIGNING_KEY` | no | HS256 key signing auth tokens (shared by user-service and api-gateway). Defaults to a committed dev-only key — fine locally, never for public deployments. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | no | If both set, user-service bootstraps an admin account on startup (unlocks the admin features: feature flags, runtime settings, all-roadmaps view). |
| `LLM_API_URL`, `LLM_MODEL`, `LLM_API_KEY` | no | Override the LM Studio fallback provider (only relevant when no Groq/Logos key is set). |

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
- **Grafana:** http://localhost:3001 — login with `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` from `infra/.env`
- **Prometheus:** http://localhost:9090

See [MONITORING.md](MONITORING.md) for the dashboards, alert rules, and log pipeline.

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
- A git-ignored `infra/.env` file — `make k8s-deploy` reads it and passes the values to Helm via `--set`, so no secret ever lands in the repo:

| Variable | Required | Behavior if unset |
|---|---|---|
| `GROQ_API_KEY` or `LOGOS_API_KEY` | for roadmap generation | Deploy prints a warning; LLM calls will fail. Ask a teammate for a key. |
| `POSTGRES_PASSWORD` | no | A random ephemeral password is generated each deploy — **data resets on redeploy**. Set it for a stable password. |
| `POSTGRES_REPLICATION_PASSWORD` | no | Random ephemeral password is generated. |
| `JWT_SIGNING_KEY` | no | A random ephemeral key is generated each deploy — **all users are logged out on redeploy**. Set it to keep sessions across deploys. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | no | No admin account is bootstrapped. |

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

#### AET Kubernetes Cluster (Production)

The AET cluster runs the **same Helm chart** with `values-aet.yaml`: ingress with TLS instead of NodePort, and PostgreSQL high availability (postgres-0 primary + postgres-1 replica with streaming replication).

**Deployed application:**

- **App:** https://team-devvopps.stud.k8s.aet.cit.tum.de
- **API:** https://team-devvopps.stud.k8s.aet.cit.tum.de/api
- **Grafana:** https://team-devvopps.stud.k8s.aet.cit.tum.de/grafana

##### Option 1: GitHub Actions (recommended — all credentials already in GitHub)

Every push to `main` builds all Docker images (`build.yml`) and then **automatically deploys** to the cluster (`deploy-k8s.yml` is called by `build.yml` after a successful build). To deploy without pushing:

1. GitHub repo → **Actions** tab
2. Select **"Deploy to AET Kubernetes Cluster"**
3. Click **"Run workflow"** and check the logs for success

The workflow connects to the cluster with its own credentials, so your local kubectl context does not matter for this path. All values come from GitHub Secrets/Variables — the workflow **fails fast** if a required one is missing (it never falls back to default credentials):

| Name | Type | Required | Description |
|---|---|---|---|
| `KUBECONFIG` | secret | yes | Kubeconfig for AET cluster access |
| `POSTGRES_PASSWORD` | secret | yes | PostgreSQL superuser password |
| `POSTGRES_REPLICATION_PASSWORD` | secret | yes | Streaming replication password |
| `POSTGRES_USER` | secret | no (default `postgres`) | PostgreSQL superuser name |
| `POSTGRES_REPLICATION_USER` | secret | no (default `replication`) | Replication username |
| `GRAFANA_ADMIN_USER` | secret | yes | Grafana admin username (Grafana is publicly exposed via ingress) |
| `GRAFANA_ADMIN_PASSWORD` | secret | yes | Grafana admin password |
| `JWT_SIGNING_KEY` | secret | yes | Random ≥32-char HS256 key — user-service signs auth tokens with it, api-gateway verifies them |
| `GROQ_API_KEY` | secret | one of these two | Groq API key for the llm-service |
| `LOGOS_API_KEY` | secret | one of these two | TUM Logos API key for the llm-service |
| `ADMIN_EMAIL` | secret | no | Bootstraps an admin account on user-service startup |
| `ADMIN_PASSWORD` | secret | no | Password for the bootstrapped admin account |
| `K8S_NAMESPACE` | variable | no (default `team-devvopps`) | Kubernetes namespace |

##### Option 2: Manual Helm deploy

1. Contact the team to get: the kubeconfig file (`stud.yaml`), PostgreSQL superuser + replication credentials, Grafana admin credentials, a Groq API key, and the JWT signing key.

2. Deploy:

```bash
export KUBECONFIG=~/path/to/stud.yaml
POSTGRES_USER=<user> POSTGRES_PASSWORD=<password> \
POSTGRES_REPLICATION_USER=<user> POSTGRES_REPLICATION_PASSWORD=<password> \
GRAFANA_ADMIN_USER=<user> GRAFANA_ADMIN_PASSWORD=<password> \
GROQ_API_KEY=<key> JWT_SIGNING_KEY=<key> \
make helm-install-aet
```

`LOGOS_API_KEY`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD` are optional extras. Note that `helm-install-aet` runs a fresh `helm install` — to upgrade an existing release, use the GitHub Actions workflow (it runs `helm upgrade --install`).

3. Verify:

```bash
kubectl get pods -n team-devvopps
kubectl get services -n team-devvopps
```

Expected: one pod per service, plus `postgres-0` and `postgres-1` (primary + replica with streaming replication).

##### How credentials work per environment

| Environment | Where credentials come from | Weak defaults possible? |
|---|---|---|
| **AET via GitHub Actions** | GitHub Secrets, injected with `--set` by the deploy workflow | No — workflow fails fast if secrets are missing |
| **AET manual (`make helm-install-aet`)** | Env vars passed to make, injected with `--set` | No — make fails without them; helm templates also `require` non-empty passwords (`values-aet.yaml` sets them to `""`) |
| **Local Kubernetes (`make k8s-deploy`)** | `infra/.env` if set, otherwise generated ephemeral values | Yes, and that's fine — local cluster is not reachable from the internet |
| **Local Docker (`make docker-up`)** | `infra/.env` (required — compose fails fast without `POSTGRES_PASSWORD`/`GRAFANA_ADMIN_PASSWORD`); JWT falls back to a dev-only key | Only the JWT dev key, which is fine — localhost only |

##### Inspecting the cluster

To inspect the server or deploy manually, switch your context to the AET cluster first:

```bash
kubectl config use-context stud

kubectl get pods -n team-devvopps       # Inspect what is running on the server
```

> ⚠️ Remember to switch back with `kubectl config use-context docker-desktop`
> before working locally again.

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

Swagger UI is available at `/swagger-ui.html` for each service.

#### Docker Compose and local development

When running with Docker Compose (`make docker-up`) or running the services locally (`make dev`):

| Service | Local URL |
|---|---|
| User Service | http://localhost:8081/swagger-ui.html |
| Course Service | http://localhost:8082/swagger-ui.html |
| Roadmap Service | http://localhost:8083/swagger-ui.html |
| LLM Service | http://localhost:8084/docs |

#### AET Kubernetes Cluster

When deployed to the AET Kubernetes cluster, access the services using `kubectl port-forward`:

```bash
# Terminal 1: user-service
kubectl port-forward svc/user-service 8081:8081 -n team-devvopps
# Terminal 2: course-service
kubectl port-forward svc/course-service 8082:8082 -n team-devvopps
# Terminal 3: roadmap-service
kubectl port-forward svc/roadmap-service 8083:8083 -n team-devvopps
# Terminal 4: llm-service
kubectl port-forward svc/llm-service 8084:8084 -n team-devvopps
```

The Swagger UIs are then available locally at:
- http://localhost:8081/swagger-ui.html
- http://localhost:8082/swagger-ui.html
- http://localhost:8083/swagger-ui.html
- http://localhost:8084/docs

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

#### User Service

- `POST /users` — Create a new user
- `GET /users` — Get all users
- `GET /users/{id}` — Get a user by ID
- `DELETE /users/{id}` — Delete a user by ID

#### Course Service

- `GET /courses` — Get all courses
- `GET /courses/{id}` — Get a course by ID
- `GET /courses/search?title=<title>` — Search courses by title

#### Roadmap Service

- `GET /roadmaps` — Get all roadmaps
- `POST /roadmaps/generate?userId=<id>&goal=<goal>` — Generate a roadmap using the LLM service
- `GET /roadmaps/{id}` — Get a roadmap by ID
- `PATCH /roadmaps/{roadmapId}/tasks/{taskId}/complete` — Toggle task completion status
- `GET /roadmaps/{roadmapId}/progress` — Get roadmap progress statistics

#### LLM Service (not exposed through gateway)

- `POST /recommend` — Generate an AI-powered roadmap recommendation
- `GET /health` — Check LLM service health and active model
- `GET /usage/{user_id}` — Get token usage and remaining quota for a user
- `GET /logs` — Retrieve recent service logs
- `GET /` — Get service information

## CI/CD

GitHub Actions workflows are defined in `.github/workflows/`.

| Workflow | Trigger | Purpose |
|---|---|---|
| `lint.yml` | Pull requests to `main`, pushes to non-`main` branches | Runs frontend ESLint, Java checks, actionlint, Helm lint, and OpenAPI lint |
| `build.yml` | Push to `main`, manual dispatch | Builds Docker images for all services, pushes them to GHCR, then calls `deploy-k8s.yml` |
| `deploy-vm.yml` | Automatically after `build.yml` completes successfully on `main` (via `workflow_run`) | Temporarily opens SSH access for the runner's IP, deploys the latest images to the Azure VM with Docker Compose, then closes SSH access again |
| `deploy-k8s.yml` | Called by `build.yml` after a successful image build on `main` (via `workflow_call`); manual dispatch | Deploys the Helm chart to the AET Kubernetes cluster |
| `provision.yml` | Manual dispatch | Provisions or imports Azure resources with Terraform, temporarily opens SSH for the runner's IP, configures the VM with Ansible, and updates the Azure public IP GitHub variable, then closes SSH access again |

Required GitHub configuration:

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
| `POSTGRES_PASSWORD` | secret | Database password — `deploy-vm.yml` refuses to deploy without it (no default/fallback credential) |

For AET Kubernetes: see the full table in
[AET Kubernetes Cluster (Production)](#aet-kubernetes-cluster-production) under Setup Instructions.

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
   - `build.yml`: Creates 5 Docker images → pushes to ghcr.io
   - `deploy-vm.yml`: Temporarily allows the runner's IP through the VM's NSG → Copies docker-compose to VM → starts services with Traefik for HTTPS

**Stack on VM:**
- Traefik (reverse proxy + Let's Encrypt HTTPS)
- Docker socket proxy (`tecnativa/docker-socket-proxy`): brokers Traefik's
  access to the Docker socket read-only, scoped to just the container-list
  API it needs for service discovery — Traefik never mounts the real
  socket directly
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

### AET Kubernetes Cluster

Deployment to the AET cluster is fully documented in
[AET Kubernetes Cluster (Production)](#aet-kubernetes-cluster-production) under Setup Instructions:

- Two deployment options: GitHub Actions (automatic on every push to `main`) or manual Helm command
- **PostgreSQL High Availability:** postgres-0 (primary) + postgres-1 (replica) with streaming replication
  - Replicas stay in sync automatically
  - On primary failure, Kubernetes restarts it and replication resumes
- Supports multiple environments with Helm values files (`values.yaml`, `values-local.yaml`, `values-aet.yaml`)

## Monitoring and Observability

The monitoring stack — **Prometheus** (metrics + alert rules), **Grafana** (dashboards), **Loki** + **Promtail** (log aggregation) — is deployed automatically by both the Docker Compose setup and the Helm chart. Six pre-built dashboards (Request Metrics, System Health, Logs, LLM Service, Auth & Security) and the alert rules (`ServiceDown`, `HighErrorRate`, `HighLatency`) are provisioned on startup, with the exported dashboard JSON in `helm/team-devvopps/grafana-dashboards/` and the alert rule files in `infra/prometheus/alerts.yaml` (compose) and `helm/team-devvopps/templates/prometheus/alerts.yaml` (Kubernetes).

**Full guide: [MONITORING.md](MONITORING.md)** — dashboard explanations, alert thresholds, how logs flow, how to test alerts, and troubleshooting.

### Accessing the dashboards

| Environment | Grafana | Prometheus |
|---|---|---|
| Docker Compose | http://localhost:3001 (credentials from `infra/.env`) | http://localhost:9090 |
| Kubernetes (local & AET via kubectl) | `kubectl port-forward -n team-devvopps svc/grafana 3001:3000` | `kubectl port-forward -n team-devvopps svc/prometheus 9090:9090` |
| AET cluster (public) | https://team-devvopps.stud.k8s.aet.cit.tum.de/grafana | port-forward only (not publicly exposed) |

### Local Logs

`make dev` writes Spring Boot logs to `logs/` (one file per service, e.g. `logs/api-gateway.log`).

## Student Responsibilities

Every student contributor is responsible for keeping the project runnable, documented, and reviewable.

- Work on feature branches and open pull requests into `main`.
- Keep changes small enough to review and describe the tested behavior in the PR.
- Run relevant local checks before requesting review.
- Update OpenAPI specs in `api/` whenever service endpoints or request/response shapes change.
- Update this README or `MONITORING.md` whenever setup, deployment, monitoring, ports, secrets, or operational commands change.
- Keep Docker, Kubernetes, Helm, Terraform, and Ansible files aligned with the services they deploy.
- Do not commit real credentials, kubeconfig files, private keys, or production secrets.
- Monitor GitHub Actions after pushing or merging and fix failing pipelines quickly.

## Making Changes

- **Code changes:** Commit to feature branches, open PR, merge to main
- **Linting:** Automatically runs on PR
- **Docker images:** Automatically built and pushed to ghcr.io on merge
- **Deployments:** Automatic to Azure VM, manual/automatic to AET cluster

### Local Pre-commit Hooks

Developer can install pre-commit hooks to run Lint OpenAPI specifications checks locally  before committing:

```bash
npm install
pip install pre-commit 
pre-commit install
```

After installation, configured checks run automatically on `git commit`. They can also be executed manually for all files:

```bash
pre-commit run -a
```
