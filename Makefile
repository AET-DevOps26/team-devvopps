NAMESPACE = team-devvopps
IMAGE_PREFIX = team-devvopps

.PHONY: help helm-install-aet helm-delete k8s-build k8s-deploy k8s-seed k8s-down docker-up docker-down dev dev-stop k8s-status

help:
	@echo "Available commands:"
	@echo ""
	@echo "Helm (AET deployment):"
	@echo "  make helm-install-aet  - Deploy to AET cluster using Helm"
	@echo "  make helm-delete       - Delete Helm deployment"
	@echo ""
	@echo "Kubernetes (local, docker-desktop):"
	@echo "  make k8s-build       - Build all Docker images for Kubernetes"
	@echo "  make k8s-deploy      - Build images and deploy locally via Helm chart"
	@echo "  make k8s-down        - Tear down Kubernetes deployment"
	@echo ""
	@echo "Docker Compose:"
	@echo "  make docker-up    - Start full stack with Docker Compose"
	@echo "  make docker-down  - Stop Docker Compose stack"
	@echo ""
	@echo "Local Development:"
	@echo "  make dev          - Start all Spring Boot services locally (background)"
	@echo ""
	@echo "Testing:"
	@echo "  make test          - Run all backend and LLM service tests"
	@echo "  make test-server   - Run all Spring Boot tests"
	@echo "  make test-user     - Run user-service tests"
	@echo "  make test-course   - Run course-service tests"
	@echo "  make test-roadmap  - Run roadmap-service tests"
	@echo "  make test-gateway  - Run api-gateway tests"
	@echo "  make test-llm      - Run LLM service pytest tests"
	@echo "  make test-client   - Run React/Vitest client tests"

# ── Helm ───────────────────────────────────────────────────────────────────────

helm-install-aet:
	@echo "Installing Helm chart to AET Kubernetes cluster..."
	@if [ -z "$(POSTGRES_USER)" ] || [ -z "$(POSTGRES_PASSWORD)" ] || [ -z "$(POSTGRES_REPLICATION_USER)" ] || [ -z "$(POSTGRES_REPLICATION_PASSWORD)" ] || [ -z "$(GRAFANA_ADMIN_USER)" ] || [ -z "$(GRAFANA_ADMIN_PASSWORD)" ] || [ -z "$(GROQ_API_KEY)" ] || [ -z "$(JWT_SIGNING_KEY)" ]; then \
		echo ""; \
		echo "ERROR: Database, Grafana, Groq, and JWT credentials required"; \
		echo ""; \
		echo "Usage: POSTGRES_USER=<user> POSTGRES_PASSWORD=<pass> POSTGRES_REPLICATION_USER=<user> POSTGRES_REPLICATION_PASSWORD=<pass> GRAFANA_ADMIN_USER=<user> GRAFANA_ADMIN_PASSWORD=<pass> GROQ_API_KEY=<key> JWT_SIGNING_KEY=<key> make helm-install-aet"; \
		echo ""; \
		echo "OR use GitHub Actions (recommended):"; \
		echo "  1. Set GitHub Secrets: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_REPLICATION_USER, POSTGRES_REPLICATION_PASSWORD, GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD, GROQ_API_KEY, JWT_SIGNING_KEY"; \
		echo "  2. Push to main or trigger workflow manually"; \
		echo ""; \
		exit 1; \
	fi
	@echo "Using credentials: username=$(POSTGRES_USER)"
	helm install team-devvopps helm/team-devvopps/ \
		-f helm/team-devvopps/values-aet.yaml \
		--set postgres.credentials.username=$(POSTGRES_USER) \
		--set postgres.credentials.password=$(POSTGRES_PASSWORD) \
		--set postgres.replicationUser=$(POSTGRES_REPLICATION_USER) \
		--set postgres.replicationPassword=$(POSTGRES_REPLICATION_PASSWORD) \
		--set grafana.adminUser=$(GRAFANA_ADMIN_USER) \
		--set grafana.adminPassword=$(GRAFANA_ADMIN_PASSWORD) \
		--set llmService.groqApiKey=$(GROQ_API_KEY) \
		--set llmService.logosApiKey=$(LOGOS_API_KEY) \
		--set auth.jwtSigningKey=$(JWT_SIGNING_KEY) \
		--set auth.adminEmail=$(ADMIN_EMAIL) \
		--set auth.adminPassword=$(ADMIN_PASSWORD) \
		-n team-devvopps
	@echo ""
	@echo "Deployment complete!"
	@echo "Check status with: kubectl get pods -n team-devvopps"

helm-delete:
	@echo "Deleting Helm deployment..."
	helm uninstall team-devvopps -n team-devvopps

# ── Kubernetes ────────────────────────────────────────────────────────────────

k8s-build:
	docker build -t $(IMAGE_PREFIX)/user-service:latest     -f server/user-service/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/course-service:latest   -f server/course-service/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/roadmap-service:latest  -f server/roadmap-service/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/api-gateway:latest      -f server/api-gateway/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/llm-service:latest      server/llm-service/
	docker build -t $(IMAGE_PREFIX)/client:latest           client/
	docker build -t $(IMAGE_PREFIX)/course-seeder:latest    -f server/course-service/Dockerfile.seeder server/course-service/

# Deploy locally with the SAME Helm chart used on the AET cluster.
# Only the values differ (values-local.yaml: local images, no ingress, single postgres).
# The Groq key is read from the git-ignored infra/.env and passed via --set,
# so it never ends up in the repo.
k8s-deploy: k8s-build
	@if [ "$$(kubectl config current-context)" != "docker-desktop" ]; then \
		echo "ERROR: kubectl context is '$$(kubectl config current-context)', expected 'docker-desktop'."; \
		echo "Switch with: kubectl config use-context docker-desktop"; \
		exit 1; \
	fi
	@GROQ_KEY=$$(grep -E '^GROQ_API_KEY=' infra/.env 2>/dev/null | cut -d= -f2-); \
	LOGOS_KEY=$$(grep -E '^LOGOS_API_KEY=' infra/.env 2>/dev/null | cut -d= -f2-); \
	if [ -z "$$GROQ_KEY" ] && [ -z "$$LOGOS_KEY" ]; then echo "WARNING: no GROQ_API_KEY or LOGOS_API_KEY in infra/.env — LLM calls will fail"; fi; \
	if [ -n "$$LOGOS_KEY" ]; then echo "NOTE: LOGOS_API_KEY set — llm-service will use TUM Logos (needs eduVPN off-campus). Comment it out in infra/.env to use Groq."; fi; \
	JWT_KEY=$$(grep -E '^JWT_SIGNING_KEY=' infra/.env 2>/dev/null | cut -d= -f2-); \
	if [ -z "$$JWT_KEY" ]; then JWT_KEY=$$(openssl rand -hex 32); echo "NOTE: no JWT_SIGNING_KEY in infra/.env — generated an ephemeral key (sessions reset each deploy)"; fi; \
	ADMIN_EMAIL=$$(grep -E '^ADMIN_EMAIL=' infra/.env 2>/dev/null | cut -d= -f2-); \
	ADMIN_PASSWORD=$$(grep -E '^ADMIN_PASSWORD=' infra/.env 2>/dev/null | cut -d= -f2-); \
	POSTGRES_PW=$$(grep -E '^POSTGRES_PASSWORD=' infra/.env 2>/dev/null | cut -d= -f2-); \
	if [ -z "$$POSTGRES_PW" ]; then POSTGRES_PW=$$(openssl rand -hex 16); echo "NOTE: no POSTGRES_PASSWORD in infra/.env — generated an ephemeral password (data resets each deploy)"; fi; \
	REPL_PW=$$(grep -E '^POSTGRES_REPLICATION_PASSWORD=' infra/.env 2>/dev/null | cut -d= -f2-); \
	if [ -z "$$REPL_PW" ]; then REPL_PW=$$(openssl rand -hex 16); fi; \
	helm upgrade --install team-devvopps helm/team-devvopps/ \
		-f helm/team-devvopps/values-local.yaml \
		--set llmService.groqApiKey="$$GROQ_KEY" \
		--set llmService.logosApiKey="$$LOGOS_KEY" \
		--set auth.jwtSigningKey="$$JWT_KEY" \
		--set auth.adminEmail="$$ADMIN_EMAIL" \
		--set auth.adminPassword="$$ADMIN_PASSWORD" \
		--set postgres.credentials.password="$$POSTGRES_PW" \
		--set postgres.replicationPassword="$$REPL_PW" \
		-n $(NAMESPACE) --create-namespace \
		--wait --timeout 5m
	@echo ""
	@echo "Restarting deployments to pick up freshly built images..."
	kubectl rollout restart deployment -n $(NAMESPACE)
	kubectl rollout status deployment -n $(NAMESPACE) --timeout=5m
	@echo ""
	@echo "Deployment complete!"
	@echo "  Client:      http://localhost:30000"
	@echo "  API Gateway: http://localhost:30080"
	@echo "  Seeder:      kubectl logs -n $(NAMESPACE) job/course-seeder -f"

# Re-run the course seeder (renders the job from the Helm chart)
k8s-seed:
	@echo "Running course seeder (checks if data exists first)..."
	kubectl delete job course-seeder -n $(NAMESPACE) --ignore-not-found
	helm template team-devvopps helm/team-devvopps/ \
		-f helm/team-devvopps/values-local.yaml \
		-s templates/seeder/job.yaml | kubectl apply -f -
	@echo "Seeder job started. Follow with: kubectl logs -n $(NAMESPACE) job/course-seeder -f"

k8s-down:
	kubectl delete namespace $(NAMESPACE)

k8s-status:
	kubectl get pods -n $(NAMESPACE)
	kubectl get services -n $(NAMESPACE)

# ── Docker Compose ────────────────────────────────────────────────────────────

docker-up:
	cd infra && docker-compose up --build

docker-down:
	cd infra && docker-compose down

docker-reset:
	cd infra && docker-compose down -v

# ── Local Development ─────────────────────────────────────────────────────────

dev:
	@echo "Starting all Spring Boot services in background..."
	@mkdir -p logs
	cd server && ./gradlew :user-service:bootRun    > ../logs/user-service.log    2>&1 & echo "user-service    → logs/user-service.log"
	cd server && ./gradlew :course-service:bootRun  > ../logs/course-service.log  2>&1 & echo "course-service  → logs/course-service.log"
	cd server && ./gradlew :roadmap-service:bootRun > ../logs/roadmap-service.log 2>&1 & echo "roadmap-service → logs/roadmap-service.log"
	cd server && ./gradlew :api-gateway:bootRun     > ../logs/api-gateway.log     2>&1 & echo "api-gateway     → logs/api-gateway.log"
	@echo ""
	@echo "All services starting. Follow logs with:"
	@echo "  tail -f logs/user-service.log"
	@echo "  tail -f logs/api-gateway.log"

dev-stop:
	@pkill -f "gradlew" || true
	@pkill -f "bootRun" || true
	@echo "Spring Boot processes stopped."

# ── Testing ───────────────────────────────────────────────────────────────────

test:
	@echo "Running all tests..."
	$(MAKE) test-server
	$(MAKE) test-llm
	$(MAKE) test-client

test-server:
	@echo "Running all Spring Boot tests..."
	cd server && $(GRADLE) test

test-user:
	@echo "Running user-service tests..."
	cd server && $(GRADLE) :user-service:test

test-course:
	@echo "Running course-service tests..."
	cd server && $(GRADLE) :course-service:test

test-roadmap:
	@echo "Running roadmap-service tests..."
	cd server && $(GRADLE) :roadmap-service:test 

test-gateway:
	@echo "Running api-gateway tests..."
	cd server && $(GRADLE) :api-gateway:test 

test-llm:
	@echo "Running LLM service tests..."
	cd server/llm-service && \
	pip install -r requirements.txt && \
	pip install -r requirements-test.txt && \
	pytest test_llm_service.py -v

test-client:
	@echo "Running client tests..."
	cd client && npm install && npm test -- --run


ifeq ($(OS),Windows_NT)
GRADLE=gradlew.bat
else
GRADLE=./gradlew
endif
