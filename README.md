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
make docker-reset   # Stop and delete all data
```

Access:
- **React Client:** http://localhost:3000
- **API Gateway:** http://localhost:8080

---

### Running with Kubernetes

#### Local Kubernetes With Helm

The fastest containerized way to run the entire stack.

Prerequisites:
- **Docker Desktop** with **Kubernetes enabled**
  - Settings → Kubernetes → Enable Kubernetes → Apply & Restart
- **Helm 3.x** (install from https://helm.sh/docs/intro/install/)


First time setup:

1. Copy the example secrets file:
```bash
cp helm/team-devvopps/values-secrets.example.yaml helm/team-devvopps/values-secrets.yaml
```

2. Edit `helm/team-devvopps/values-secrets.yaml` with your database credentials:
```yaml
postgres:
  credentials:
    username: postgres
    password: your_secure_password_here
  # Replication uses trust auth (no password needed within cluster)
  replicationUser: replication
  replicationPassword: replication
```

3. Deploy:
```bash
make helm-install
```

Access:
- React Client: http://localhost:30000
- API Gateway: http://localhost:30080

Manage deployment:
```bash
make helm-upgrade    # Update existing deployment
make helm-delete     # Remove deployment
make k8s-status      # Check pod status
```

#### AET Kubernetes Cluster

For deployment to the AET cluster, see [DEPLOYMENT.md](DEPLOYMENT.md)

```bash
make helm-install-aet    # Deploy to AET (uses GitHub Secrets)
```

#### Legacy kubectl (Not Recommended)

```bash
make k8s-build        # Build all Docker images
make k8s-deploy       # Deploy to Kubernetes
make k8s-status       # Check status
make k8s-down         # Tear down
```

---

## API Documentation

OpenAPI specifications are maintained in `api/`:

- `api/user-service.yaml`
- `api/course-service.yaml`
- `api/roadmap-service.yaml`
- `api/genai-service.yaml`

Swagger UI is available at `/swagger-ui.html` on each service:

| Service | Local URL |
|---|---|
| User Service | http://localhost:8081/swagger-ui.html |
| Course Service | http://localhost:8082/swagger-ui.html |
| Roadmap Service | http://localhost:8083/swagger-ui.html |
| LLM Service | http://localhost:8084/swagger-ui.html |

The API Gateway exposes the current implemented service routes:

| Gateway Route | Target Service | Notes |
|---|---|---|
| `/users/**` | `user-service` | Create and fetch users |
| `/courses/**` | `course-service` | List courses, fetch course by ID, search by title |
| `/roadmaps/**` | `roadmap-service` | Generate and retrieve roadmaps |

Current implemented endpoints include:

- `POST /users`
- `GET /users`
- `GET /users/{id}`
- `GET /courses`
- `GET /courses/{id}`
- `GET /courses/search?title=<title>`
- `POST /roadmaps/generate?userId=<id>&goal=<goal>`
- `GET /roadmaps/{id}`

OpenAPI linting runs in CI with Redocly:

```bash
redocly lint api/user-service.yaml
redocly lint api/course-service.yaml
redocly lint api/roadmap-service.yaml
redocly lint api/genai-service.yaml
```

## CI/CD

GitHub Actions workflows are defined in `.github/workflows/`.

| Workflow | Trigger | Purpose |
|---|---|---|
| `lint.yml` | Pull requests to `main`, pushes to non-`main` branches | Runs frontend ESLint, Java checks, actionlint, Helm lint, and OpenAPI lint |
| `build.yml` | Push to `main` | Builds Docker images for all services and pushes them to GHRC |
| `deploy-vm.yml` | Automatically after `build.yml` completes successfully on `main` (via `workflow_run`) | Temporarily opens SSH access for the runner's IP, deploys the latest images to the Azure VM with Docker Compose, then closes SSH access again |
| `deploy-k8s.yml` | Push to `main`, manual dispatch | Deploys the Helm chart to the AET Kubernetes cluster |
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

For AET Kubernetes:

| Name | Type | Description |
|---|---|---|
| `KUBECONFIG` | secret | Kubeconfig for AET cluster access |
| `POSTGRES_USER` | secret | Database username |
| `POSTGRES_PASSWORD` | secret | Database password |
| `K8S_NAMESPACE` | variable | Kubernetes namespace (`team-devvopps`) |

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

See [README.md section on Azure](README.md#azure-vm-stagingdemo) for detailed setup.

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
