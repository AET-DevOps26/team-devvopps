# team-devvopps
Repository for team devvopps

## 🏗️ System Architecture

### Microservices
The backend consists of **3 independent Spring Boot microservices**:

- **user-service** (port 8081): User management 
- **course-service** (port 8082): Mock TUM course database (source of truth for university courses)
- **roadmap-service** (port 8083): Personalized learning roadmap generation and tracking

Each service has:
- Its own PostgreSQL database (userdb, coursedb, roadmapdb)
- Independent entity models with auto-created schema
- REST API endpoints (to be implemented)

**Entity Relationships:** See the UML diagram in `/server/docs/` for complete data model and relationships.

---

## Project Execution Guide

This project uses **Java 25 (Spring Boot)** for the backend microservices and **Node.js (React/Vite)** for the frontend. **Docker** is used for PostgreSQL database management.

### Prerequisites
- **Docker Desktop** (Must be open and running)
- **Miniconda** or **Anaconda** (To quickly set up the development environment)
- **Java 25** (included in conda environment)

---

### 1. Setup (Environment Preparation)
To create an isolated Conda environment containing all dependencies, run the following command in your terminal:
```bash
conda create -y -n team-devvopps -c conda-forge openjdk=25 nodejs
```

---

### 2. Running the Backend (Spring Boot Microservices)

First, **ensure that Docker Desktop is running**.

#### Step 1: Start PostgreSQL with all 3 databases
Open a terminal and run:
```bash
conda activate team-devvopps
cd server
docker-compose up
```
This starts PostgreSQL on port 5432 with 3 databases: userdb, coursedb, roadmapdb.
Leave this terminal running.

#### Step 2: Start user-service (port 8081)
Open a **new terminal** and run:
```bash
conda activate team-devvopps
cd server/user-service
../gradlew bootRun
```

#### Step 3: Start course-service (port 8082)
Open another **new terminal** and run:
```bash
conda activate team-devvopps
cd server/course-service
../gradlew bootRun
```

#### Step 4: Start roadmap-service (port 8083)
Open another **new terminal** and run:
```bash
conda activate team-devvopps
cd server/roadmap-service
../gradlew bootRun
```

**Status Check:** When all 3 services show "Started successfully", the backend is ready.

*Note: When the process remains at the `> 80% EXECUTING > :bootRun` stage, it means the service has successfully started up and is listening for requests. This is not an error.*

**Services available at:**
- user-service: http://localhost:8081
- course-service: http://localhost:8082
- roadmap-service: http://localhost:8083

**Database credentials (local development only):**
- Host: localhost
- Port: 5432
- Username: postgres
- Password: postgres

---

### 3. Running the Frontend (React/Vite)
Open a **new terminal window** and follow these steps:

```bash
# 1. Activate the environment
conda activate team-devvopps

# 2. Navigate to the client folder
cd client

# 3. Install dependencies
npm install

# 4. Start the development server
npm run dev
```

Once the application is running successfully, you can access the user interface using your browser at `http://localhost:3000`.

---

## 🔄 Next Steps

1. **Populate mock course data** - Create data.sql with TUM course information
2. **Implement REST API endpoints** - Add controllers and services for each microservice
3. **Service communication** - Enable inter-service API calls
4. **Docker containerization** - Create Dockerfiles for all services
5. **Single-command deployment** - Update compose.yaml to orchestrate all services as containers
