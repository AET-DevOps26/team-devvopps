# team-devvopps
Repository for team devvopps

## System Architecture

### Microservices
The backend consists of **3 independent Spring Boot microservices**:

- **user-service** (port 8081): User management 
- **course-service** (port 8082): Mock TUM course database (source of truth for university courses)
- **roadmap-service** (port 8083): Personalized learning roadmap generation and tracking

Each service has:
- Its own PostgreSQL database (userdb, coursedb, roadmapdb)
- Independent entity models with auto-created schema
- REST API endpoints 

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
To create an isolated Conda environment containing all dependencies (Java, Node.js, and Python packages), run:
```bash
conda env create -f environment.yml
conda activate team-devvopps
```

---

### 2. Running the Backend (Spring Boot Microservices)

First, **ensure that Docker Desktop is running**.

#### Step 1: Start PostgreSQL
Open a terminal and run:
```bash
conda activate team-devvopps
cd server
docker-compose up
```
This starts PostgreSQL on port 5432 with 3 databases: userdb, coursedb, roadmapdb.
Leave this terminal running.

#### Step 2: Build the Spring Boot services and create databases
First, build the Spring Boot services by running:
```bash
conda activate team-devvopps
cd server/user-service
../gradlew bootRun
cd ../course-service
../gradlew bootRun
cd ../roadmap-service
../gradlew bootRun
```

**Status Check:** When all 3 services show "Started successfully", the backend is ready.

*Note: When the process remains at the `> 80% EXECUTING > :bootRun` stage, it means the service has successfully started up and is listening for requests. This is not an error.*

**Services available at:**
- user-service: http://localhost:8081
- course-service: http://localhost:8082
- roadmap-service: http://localhost:8083


#### Step 3: Populate course database

Once PostgreSQL is running and services are built, open a **new terminal** to populate the coursedb with real TUM Master Informatik courses:

** For macOS / Linux:**
```bash
conda activate team-devvopps
cd server/course-service
python3 fetch_and_seed_courses.py for 
```
**For Windows:**
```bash
conda activate team-devvopps
cd server/course-service
python fetch_and_seed_courses.py for 
```
This fetches ~930 courses from the TUM Campus Online API and inserts them into the coursedb. The data persists across Docker restarts. You only need to run this once, but you can re-run it anytime to refresh the course data.

*Optional: To verify the courses were loaded, run `python3 browse_courses.py` (or `python browse_courses.py` if using Windows) for an interactive course browser.*


### Regenerating/Resetting the Database
If you want to reset the database and start fresh, run:
```bash
cd server
docker-compose down
docker volume rm server_postgres_data
```
Then do Step 2 and 3 again. 

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

1. **Implement REST API endpoints** - Add controllers and services for each microservice
2. **Service communication** - Enable inter-service API calls
3. **Docker containerization** - Create Dockerfiles for all services
4. **Single-command deployment** - Update compose.yaml to orchestrate all 3 services as containers
