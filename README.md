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

## Running with Kubernetes (Recommended)

The fastest way to run the entire stack. Requires Docker Desktop with Kubernetes enabled.

### Prerequisites
- **Docker Desktop** with **Kubernetes enabled**
  - Docker Desktop → Settings → Kubernetes → Enable Kubernetes → Apply & Restart

### 1. Verify Kubernetes is running
```bash
kubectl get nodes
# Expected: docker-desktop   Ready   control-plane
```

### 2. Build images and deploy
```bash
make k8s-deploy
```
This builds all 5 Docker images and applies all Kubernetes manifests automatically. Waits until all pods are ready.

### 3. Check status (optional)
```bash
make k8s-status
```

### 5. Access the application
- **React Client:** http://localhost:30000
- **API Gateway:** http://localhost:30080

### Tearing down
```bash
make k8s-down
```

---

## Running with Docker Compose

Runs the full stack with a single command. No Kubernetes required.

### Prerequisites
- **Docker Desktop** (must be open and running)

### Start the full stack
```bash
make docker-up
```

### Access the application
- **React Client:** http://localhost:3000
- **API Gateway:** http://localhost:8080

### Stop
```bash
make docker-down
```

### Reset the database
```bash
make docker-reset
```

---

## Running Locally (Development)

For active development without Docker for the services.

### Prerequisites
- **Docker Desktop** (for PostgreSQL only)
- **Miniconda** or **Anaconda**
- **Java 25** (included in conda environment)

### 1. Setup environment
```bash
conda env create -f environment.yml
conda activate team-devvopps
```

### 2. Start PostgreSQL
```bash
conda activate team-devvopps
cd server
docker-compose up
```
This starts PostgreSQL on port 5432 with 3 databases: userdb, coursedb, roadmapdb.

### 3. Start Spring Boot services
```bash
make dev
```
All 4 services start in the background. Logs are written to `logs/` directory:
```bash
tail -f logs/user-service.log
tail -f logs/api-gateway.log
```

To stop all services:
```bash
make dev-stop
```

**Services available at:**
- API Gateway: http://localhost:8080
- user-service: http://localhost:8081
- course-service: http://localhost:8082
- roadmap-service: http://localhost:8083

### 4. Start the frontend
```bash
conda activate team-devvopps
cd client
npm install
npm run dev
```

Frontend available at: http://localhost:3000

### 5. Populate course database (first time only)
```bash
conda activate team-devvopps
cd server/course-service
python3 fetch_and_seed_courses.py   # macOS/Linux
python fetch_and_seed_courses.py    # Windows
```
Fetches ~930 courses from the TUM Campus Online API into coursedb. Only needs to be run once.

### Reset local database
```bash
cd server
docker-compose down
docker volume rm server_postgres_data
```
Then repeat steps 2–5.
