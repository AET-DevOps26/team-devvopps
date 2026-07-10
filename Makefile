NAMESPACE = team-devvopps
IMAGE_PREFIX = team-devvopps

.PHONY: help helm-install-aet helm-delete k8s-build k8s-deploy k8s-seed k8s-deploy-old k8s-secrets-old k8s-seed-old k8s-down docker-up docker-down dev dev-stop k8s-status

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
	@echo "  make k8s-deploy-old  - Same but with plain YAML manifests (infra/k8s/)"
	@echo "  make k8s-down        - Tear down Kubernetes deployment"
	@echo ""
	@echo "Docker Compose:"
	@echo "  make docker-up    - Start full stack with Docker Compose"
	@echo "  make docker-down  - Stop Docker Compose stack"
	@echo ""
	@echo "Local Development:"
	@echo "  make dev          - Start all Spring Boot services locally (background)"

# ── Helm ───────────────────────────────────────────────────────────────────────

helm-install-aet:
	@echo "Installing Helm chart to AET Kubernetes cluster..."
	@if [ -z "$(POSTGRES_USER)" ] || [ -z "$(POSTGRES_PASSWORD)" ] || [ -z "$(POSTGRES_REPLICATION_USER)" ] || [ -z "$(POSTGRES_REPLICATION_PASSWORD)" ] || [ -z "$(GRAFANA_ADMIN_USER)" ] || [ -z "$(GRAFANA_ADMIN_PASSWORD)" ] || [ -z "$(GROQ_API_KEY)" ]; then \
		echo ""; \
		echo "ERROR: Database, Grafana, and Groq credentials required"; \
		echo ""; \
		echo "Usage: POSTGRES_USER=<user> POSTGRES_PASSWORD=<pass> POSTGRES_REPLICATION_USER=<user> POSTGRES_REPLICATION_PASSWORD=<pass> GRAFANA_ADMIN_USER=<user> GRAFANA_ADMIN_PASSWORD=<pass> GROQ_API_KEY=<key> make helm-install-aet"; \
		echo ""; \
		echo "OR use GitHub Actions (recommended):"; \
		echo "  1. Set GitHub Secrets: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_REPLICATION_USER, POSTGRES_REPLICATION_PASSWORD, GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD, GROQ_API_KEY"; \
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
	if [ -z "$$GROQ_KEY" ]; then echo "WARNING: no GROQ_API_KEY in infra/.env — LLM calls will fail"; fi; \
	kubectl delete job course-seeder -n $(NAMESPACE) --ignore-not-found 2>/dev/null; \
	helm upgrade --install team-devvopps helm/team-devvopps/ \
		-f helm/team-devvopps/values-local.yaml \
		--set llmService.groqApiKey="$$GROQ_KEY" \
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

# ── Kubernetes (OLD — plain YAML manifests from infra/k8s/) ──────────────────
# Kept for reference/fallback. Uses the hand-written manifests instead of the
# Helm chart. Do NOT mix with the Helm-based k8s-deploy in the same namespace —
# wipe first with `make k8s-down` when switching between the two methods.

k8s-deploy-old: k8s-build
	kubectl apply -f infra/k8s/namespace.yaml
	$(MAKE) k8s-secrets-old
	kubectl apply -f infra/k8s/
	kubectl apply -f infra/k8s/postgres/
	kubectl apply -f infra/k8s/user-service/
	kubectl apply -f infra/k8s/course-service/
	kubectl apply -f infra/k8s/roadmap-service/
	kubectl apply -f infra/k8s/llm-service/
	kubectl apply -f infra/k8s/api-gateway/
	kubectl apply -f infra/k8s/client/
	@echo ""
	@echo "Waiting for pods to be ready..."
	kubectl wait --for=condition=ready pod --all -n $(NAMESPACE) --timeout=120s
	$(MAKE) k8s-seed-old
	@echo ""
	@echo "Deployment complete!"
	@echo "  Client:      http://localhost:30000"
	@echo "  API Gateway: http://localhost:30080"

# Create the llm-secret from infra/.env (GROQ_API_KEY) — the .env file is
# git-ignored, so the key never ends up in the repo. Safe to re-run (apply).
k8s-secrets-old:
	@if [ -f infra/.env ]; then \
		GROQ_KEY=$$(grep -E '^GROQ_API_KEY=' infra/.env | cut -d= -f2-); \
		kubectl create secret generic llm-secret -n $(NAMESPACE) \
			--from-literal=groq-api-key="$$GROQ_KEY" \
			--dry-run=client -o yaml | kubectl apply -f -; \
		echo "llm-secret created from infra/.env"; \
	else \
		echo "WARNING: infra/.env not found — llm-service will have no GROQ_API_KEY"; \
	fi

k8s-seed-old:
	@echo "Running course seeder (checks if data exists first)..."
	kubectl delete job course-seeder -n $(NAMESPACE) --ignore-not-found
	kubectl apply -f infra/k8s/seeder/job.yaml
	@echo "Seeder job started. Follow with: kubectl logs -n $(NAMESPACE) job/course-seeder -f"

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
