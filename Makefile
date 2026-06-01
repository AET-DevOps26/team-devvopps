NAMESPACE = team-devvopps
IMAGE_PREFIX = team-devvopps

.PHONY: help helm-install helm-install-aet helm-upgrade helm-delete k8s-build k8s-deploy k8s-seed k8s-down docker-up docker-down dev dev-stop k8s-status

help:
	@echo "Available commands:"
	@echo ""
	@echo "Helm (Recommended for AET deployment):"
	@echo "  make helm-install      - Deploy to local Kubernetes using Helm"
	@echo "  make helm-install-aet  - Deploy to AET cluster using Helm"
	@echo "  make helm-upgrade      - Upgrade existing Helm deployment"
	@echo "  make helm-delete       - Delete Helm deployment"
	@echo ""
	@echo "Kubernetes (Legacy):"
	@echo "  make k8s-build    - Build all Docker images for Kubernetes"
	@echo "  make k8s-deploy   - Build images and deploy to Kubernetes"
	@echo "  make k8s-down     - Tear down Kubernetes deployment"
	@echo ""
	@echo "Docker Compose:"
	@echo "  make docker-up    - Start full stack with Docker Compose"
	@echo "  make docker-down  - Stop Docker Compose stack"
	@echo ""
	@echo "Local Development:"
	@echo "  make dev          - Start all Spring Boot services locally (background)"

# ── Helm ───────────────────────────────────────────────────────────────────────

helm-install:
	@echo "Installing Helm chart to local Kubernetes..."
	helm install team-devvopps helm/team-devvopps/ -n team-devvopps --create-namespace
	@echo ""
	@echo "Deployment complete!"
	@echo "  Client:      http://localhost:30000"
	@echo "  API Gateway: http://localhost:30080"

helm-install-aet:
	@echo "Installing Helm chart to AET Kubernetes cluster..."
	helm install team-devvopps helm/team-devvopps/ \
		-f helm/team-devvopps/values-aet.yaml \
		-n team-devvopps --create-namespace
	@echo ""
	@echo "Check status with: kubectl get pods -n team-devvopps"

helm-upgrade:
	@echo "Upgrading Helm chart..."
	helm upgrade team-devvopps helm/team-devvopps/ -n team-devvopps

helm-delete:
	@echo "Deleting Helm deployment..."
	helm uninstall team-devvopps -n team-devvopps

# ── Kubernetes ────────────────────────────────────────────────────────────────

k8s-build:
	docker build -t $(IMAGE_PREFIX)/user-service:latest     -f server/user-service/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/course-service:latest   -f server/course-service/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/roadmap-service:latest  -f server/roadmap-service/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/api-gateway:latest      -f server/api-gateway/Dockerfile server/
	docker build -t $(IMAGE_PREFIX)/client:latest           client/
	docker build -t $(IMAGE_PREFIX)/course-seeder:latest    -f server/course-service/Dockerfile.seeder server/course-service/

k8s-deploy: k8s-build
	kubectl apply -f infra/k8s/namespace.yaml
	kubectl apply -f infra/k8s/
	kubectl apply -f infra/k8s/postgres/
	kubectl apply -f infra/k8s/user-service/
	kubectl apply -f infra/k8s/course-service/
	kubectl apply -f infra/k8s/roadmap-service/
	kubectl apply -f infra/k8s/api-gateway/
	kubectl apply -f infra/k8s/client/
	@echo ""
	@echo "Waiting for pods to be ready..."
	kubectl wait --for=condition=ready pod --all -n $(NAMESPACE) --timeout=120s
	$(MAKE) k8s-seed
	@echo ""
	@echo "Deployment complete!"
	@echo "  Client:      http://localhost:30000"
	@echo "  API Gateway: http://localhost:30080"

k8s-seed:
	@echo "Running course seeder (checks if data exists first)..."
	kubectl delete job course-seeder -n $(NAMESPACE) --ignore-not-found
	kubectl apply -f infra/k8s/seeder/job.yaml
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
