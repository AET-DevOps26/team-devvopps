# team-devvopps

TUMgoal is a DevOps course project for helping TUM Computer Science students turn academic or career goals into structured learning roadmaps. Students enter a goal and receive milestones, tasks, course recommendations, and progress tracking.

## Project Deliverables

> **TODO:** Turn each bullet below into a link to its README section, folder, or file,
> so a grader can jump straight from requirement to proof. Final link targets to be
> decided once the architecture/API documentation pass is done.

This repository currently includes:

- Docker Compose, Kubernetes (Helm), Azure VM, and AET cluster setup instructions.
- Architecture documentation for the React client, API gateway, Spring Boot microservices, PostgreSQL databases, and GenAI integration.
- OpenAPI specifications for the service APIs in `api/`.
- CI/CD pipelines for linting, image builds, Azure VM deployment, AET Kubernetes deployment, and Azure VM provisioning.
- A monitoring stack (Prometheus, Grafana, Loki/Promtail) with exported dashboards and alert rules, documented in [docs/MONITORING.md](docs/MONITORING.md).
- Operational instructions for logs, Kubernetes status checks, Helm release checks, and deployment troubleshooting.
- A student responsibilities section documenting who is responsible for which subsystem.

## System Architecture

**Full architecture documentation: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** —
high-level description, subsystem decomposition and interfaces, auth model, GenAI
provider architecture — and the **database schema** in
[docs/system_overview/database_schema.md](docs/system_overview/database_schema.md)
(ER diagrams per database). This section is a short summary.

> **TODO:**
>
> - Add the remaining mandatory UML diagrams to `docs/ARCHITECTURE.md`: Use Case Diagram
>   and Analysis Object Model (subsystem decomposition + ER diagrams already exist as Mermaid)
> - The summary below and the API Documentation section still describe the pre-auth system —
>   update the component descriptions, the gateway routes table (`/auth/**`, `/features/**`,
>   `/settings/**`, `/llm/**` are missing), and the endpoints list (auth endpoints; `userId`
>   now comes from the verified JWT, not a query param). `docs/ARCHITECTURE.md` §3 already
>   has the correct tables to copy from. The Swagger UI / OpenAPI specs in `api/` are up to date.

The application uses a microservice-based client-server architecture.

### Components
The backend consists of **3 independent Spring Boot microservices and 1 Python service** behind an API Gateway:

**api-gateway** (port 8080): Routes all incoming requests to the appropriate service
- **user-service** (port 8081): User management
- **course-service** (port 8082): Mock TUM course database (source of truth for university courses)
- **roadmap-service** (port 8083): Personalized learning roadmap generation and tracking
- **llm-service** (port 8084): Responsible for all AI-driven logic 

Each service has:
- Its own PostgreSQL database (userdb, coursedb, roadmapdb)
- Independent entity models with auto-created schema
- REST API endpoints

Architecture diagrams and product context are in `docs/system_overview/`, including:

- `docs/system_overview/database_schema.md`
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
├── docs/                   # Documentation deliverables
│   ├── ARCHITECTURE.md     # Architecture: subsystem decomposition, interfaces, auth, GenAI
│   ├── MONITORING.md       # Monitoring guide: dashboards, alerts, log pipeline
│   ├── system_overview/    # Problem statement, diagrams, backlog, database schema
│   ├── known-issues.md     # Known issues and their status
│   └── security/           # Security review notes
├── helm/team-devvopps/     # Helm chart used for ALL Kubernetes deployments (local + AET)
│   ├── grafana-dashboards/ # Exported Grafana dashboard JSON (also mounted by compose)
│   ├── templates/          # Helm templates for services, postgres, prometheus, grafana, loki, promtail
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
├── environment.yml         # Conda environment for local development
├── Makefile                # Common local/deployment commands
├── redocly.yaml            # Redocly OpenAPI lint configuration
└── README.md
```

---

## Quick Start

**Choose your setup method:**

- **Full stack, one command (start here):** [Running with Docker Compose](#running-with-docker-compose)
- **Kubernetes:** [Running with Kubernetes](#running-with-kubernetes)
- **Developing the services in an IDE:** [Local Development Setup](#local-development-setup-ide-optional)

---

## Setup Instructions

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
- **Grafana:** http://localhost:3001 — login with `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` from `infra/.env`. Also reachable via the **Grafana ↗** button in the app's Admin Panel (admin login required).
- **Prometheus:** http://localhost:9090

See [docs/MONITORING.md](docs/MONITORING.md) for the dashboards, alert rules, and log pipeline.

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

#### AET Kubernetes Cluster (Production)

The AET cluster runs the Helm chart with `values-aet.yaml`: ingress with TLS, and PostgreSQL high availability (postgres-0 primary + postgres-1 replica with streaming replication).

**Deployed application:**

- **App:** https://team-devvopps.stud.k8s.aet.cit.tum.de
- **API:** https://team-devvopps.stud.k8s.aet.cit.tum.de/api
- **Grafana:** https://team-devvopps.stud.k8s.aet.cit.tum.de/grafana

**Deployment is automatic:** every push to `main` builds all Docker images (`build.yml`) and then deploys to the cluster (`deploy-k8s.yml` is called by `build.yml` after a successful build). To deploy without pushing:

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

A manual Helm fallback also exists: `make helm-install-aet` (run it without variables and it prints exactly which credentials it needs). It is not part of the required workflow — use the GitHub Actions path above.

To verify a deployment:

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

```bash
kubectl config use-context stud

kubectl get pods -n team-devvopps       # Inspect what is running on the server
```

> ⚠️ Remember to switch back with `kubectl config use-context docker-desktop`
> before working locally again.

#### Local Kubernetes (extra, not a project requirement)

We also deploy the same Helm chart to Docker Desktop's local Kubernetes — an extra we used during development to test chart changes before they reach the AET cluster (`values-local.yaml`: locally built images, NodePort instead of ingress, single postgres).

Prerequisites: **Docker Desktop with Kubernetes enabled**, **Helm 3.x**, and a git-ignored `infra/.env` — `make k8s-deploy` reads it and passes values to Helm via `--set`. `GROQ_API_KEY` (or `LOGOS_API_KEY`) is needed for roadmap generation; `POSTGRES_PASSWORD` and `JWT_SIGNING_KEY` are optional (random ephemeral values are generated if unset, so data and sessions reset on each redeploy); `ADMIN_EMAIL`/`ADMIN_PASSWORD` optionally bootstrap an admin account.

```bash
kubectl config use-context docker-desktop
make k8s-deploy       # Builds all images, installs/upgrades the Helm release, restarts pods
```

Access: React Client at http://localhost:30000, API Gateway at http://localhost:30080.

```bash
make k8s-status      # Check pod status
make k8s-seed        # Re-run the course seeder
make k8s-down        # Tear down (deletes the namespace and all local data)
```

---

### Azure VM (Staging/Demo)

> **TODO:** Verify the VM deployment end-to-end. Two suspected gaps found during the docs review:
> `compose.azure.yml` sets no `JWT_SIGNING_KEY` (user-service and api-gateway fall back to the
> committed dev-only key on a publicly reachable host), and the llm-service there has no
> Groq/Logos key and points at a nonexistent `llm-backend` container — roadmap generation on
> the VM likely fails. Needs checking and probably a fix in `compose.azure.yml` + `deploy-vm.yml`.

The application is automatically built and deployed to an Azure VM on every merge to main.

**URLs:**
- **Frontend:** https://client.9.223.113.24.nip.io
- **API Gateway:** https://api.9.223.113.24.nip.io

**How it works:**

1. **Provisioning (one-time):**
   - GitHub Actions → Provision Azure VM → Run workflow
   - Terraform creates VM + Ansible configures Docker

2. **Deployment (automatic on merge to main):**
   - `build.yml`: Creates the Docker images → pushes to ghcr.io
   - `deploy-vm.yml`: Temporarily allows the runner's IP through the VM's NSG → Copies docker-compose to VM → starts services with Traefik for HTTPS

**Required GitHub configuration:**

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

---

### Local Development Setup (IDE, optional)

> This path is for actively developing the services without containers. It is **not part of
> the required setup** — to just run the system, use [Docker Compose](#running-with-docker-compose).

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

- `POST /auth/signup` - Register and starts a session
- `POST /auth/login` - Verify credentials and start a session
- `POST /auth/logout` - Clear the session cookie
- `GET /auth/me` - Returns the currently authenticated user
- `GET /auth/logs` - Recent auth events for the admin panel (admin only)

- `GET /features` - Returns all flags with their state and description
- `PUT /features/{name}` - Enables or disables a flag

- `GET /settings`- Returns all settings
- `PUT /settings/{name}` - Updates a setting's value

#### Course Service

- `GET /courses` — Get all courses
- `GET /courses/{id}` — Get a course by ID
- `GET /courses/search` — Search courses by title

#### Roadmap Service

- `GET /roadmaps` — Returns the authenticated user's own roadmaps
- `GET /roadmaps/all` - Returns every roadmap (admin only)
- `POST /roadmaps/generate` — Generate a roadmap using the LLM service
- `GET /roadmaps/{id}` — Get a roadmap by ID
- `PATCH /roadmaps/{roadmapId}/tasks/{taskId}/complete` — Toggle task completion status
- `GET /roadmaps/{roadmapId}/progress` — Get roadmap progress statistics

#### LLM Service (not exposed through gateway)

- `POST /recommend` — Generate an AI-powered roadmap recommendation
- `GET /health` — Check LLM service health and active model
- `GET /usage` — Get token usage and remaining quota for a user
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
| `vm-start.yml` | Manual dispatch | Starts the Azure VM |
| `vm-stop.yml` | Manual dispatch | Deallocates the Azure VM to stop billing |
| `testing.yml` | Pull requests to `main`, pushes to `main` | Runs Spring Boot tests for all backend services, pytest tests for the LLM service and vitest tests for the client |

Required GitHub configuration (secrets and variables) is documented per deployment target under Setup Instructions:
[AET Kubernetes Cluster (Production)](#aet-kubernetes-cluster-production) and [Azure VM (Staging/Demo)](#azure-vm-stagingdemo).

## Monitoring and Observability

The monitoring stack — **Prometheus** (metrics + alert rules), **Grafana** (dashboards), **Loki** + **Promtail** (log aggregation) — is deployed automatically by both the Docker Compose setup and the Helm chart. Five pre-built dashboards (Request Metrics, System Health, Logs, LLM Service, Auth & Security) and the alert rules (`ServiceDown`, `HighErrorRate`, `HighLatency`) are provisioned on startup, with the exported dashboard JSON in `helm/team-devvopps/grafana-dashboards/` and the alert rule files in `infra/prometheus/alerts.yaml` (compose) and `helm/team-devvopps/templates/prometheus/alerts.yaml` (Kubernetes).

**Full guide: [docs/MONITORING.md](docs/MONITORING.md)** — dashboard explanations, alert thresholds, how logs flow, how to test alerts, and troubleshooting.

### Accessing the dashboards

| Environment | Grafana | Prometheus |
|---|---|---|
| Docker Compose | http://localhost:3001 (credentials from `infra/.env`) | http://localhost:9090 |
| Kubernetes (local & AET via kubectl) | `kubectl port-forward -n team-devvopps svc/grafana 3001:3000` | `kubectl port-forward -n team-devvopps svc/prometheus 9090:9090` |
| AET cluster (public) | https://team-devvopps.stud.k8s.aet.cit.tum.de/grafana | port-forward only (not publicly exposed) |

On both Docker and the AET cluster, Grafana can also be opened from inside the app: log in as an admin and use the **Grafana ↗** button in the Admin Panel (shown when the `grafanaLink` feature flag is enabled).

### Local Logs

`make dev` writes Spring Boot logs to `logs/` (one file per service, e.g. `logs/api-gateway.log`).

## Testing

> **TODO:** Testing suite is not implemented yet. This section will document:
>
> - Unit tests for critical server-side logic (Spring Boot services)
> - Unit tests for the GenAI component (llm-service)
> - Client-side tests for core workflows and interactions
> - How to run the full test suite locally
> - How tests run automatically in the CI pipeline (on every pull request)


## Testing

The project contains test suites for all backend services and the React client. 

### Run all 

```bash
make test
```

### Run Spring Boot tests

```bash
make test-server
```

### Run tests for a specific service

```bash
cd server
make test-user 
make test-course
make test-roadmap
make test-gateway
```

### Run LLM Service Tests

```bash
make test-llm
```

### Run Client Tests

```bash
make test-client
```

## Student Responsibilities

> **TODO:** Complete the remaining items in Linn's and Hafizenur's lists before submission.

**Dilay Nurlu**

- TUM courses source-of-truth database population — `server/course-service/fetch_and_seed_courses.py`, the seeder image (`server/course-service/Dockerfile.seeder`, `requirements.seeder.txt`), and the Kubernetes seeder job (`helm/team-devvopps/templates/seeder/job.yaml`, `make k8s-seed`)
- Kubernetes deployment setup (Helm chart, local docker-desktop + AET cluster)
- Monitoring setup (Prometheus, Grafana dashboards, alert rules, Loki/Promtail logging)
- Security issue fixes

**Linn Ewen**

- API documentation (OpenAPI specs in `api/`, Swagger UI)
- Backend (Spring Boot microservices)
- Testing
- _TODO: complete this list_

**Hafizenur Sahbudak**

- LLM service (GenAI component)
- User login page
- Admin panel
- _TODO: complete this list_

In addition to their primary subsystem, every team member:

- Works on feature branches and opens pull requests into `main`.
- Keeps changes small enough to review and describes the tested behavior in the PR.
- Runs relevant local checks before requesting review.
- Updates OpenAPI specs in `api/` whenever service endpoints or request/response shapes change.
- Updates this README or `docs/MONITORING.md` whenever setup, deployment, monitoring, ports, secrets, or operational commands change.
- Keeps Docker, Kubernetes, Helm, Terraform, and Ansible files aligned with the services they deploy.
- Never commits real credentials, kubeconfig files, private keys, or production secrets.
- Monitors GitHub Actions after pushing or merging and fixes failing pipelines quickly.

## Making Changes

- **Code changes:** Commit to feature branches, open PR, merge to main
- **Linting:** Automatically runs on PR
- **Docker images:** Automatically built and pushed to ghcr.io on merge
- **Deployments:** Automatic to both the Azure VM and the AET cluster on merge to main (manual dispatch also available)

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
