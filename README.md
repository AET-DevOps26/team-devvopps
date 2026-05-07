# team-devvopps
Repository for team devvopps

## Project Execution Guide

This project uses **Java 21 (Spring Boot)** for the backend and **Node.js (React/Vite)** for the frontend. Additionally, **Docker** is used for database requirements.

### Prerequisites
- **Docker Desktop** (Must be open and running)
- **Miniconda** or **Anaconda** (To quickly set up the development environment)

---

### 1. Setup (Environment Preparation)
To create an isolated Conda environment containing all dependencies, run the following command in your terminal:
```bash
conda create -y -n team-devvopps -c conda-forge openjdk=21 nodejs
```

### 2. Running the Backend (Spring Boot)
First, **ensure that Docker Desktop is running**. Then, follow these steps sequentially in the terminal:

```bash
# 1. Activate the environment
conda activate team-devvopps

# 2. Navigate to the server folder
cd server

# 3. Grant execution permission to the gradlew file for macOS/Linux (only needed once)
chmod +x gradlew

# 4. Start the server
./gradlew bootRun
```
*Note: When the process remains at the `> 80% EXECUTING > :bootRun` stage, it means the server has successfully started up and is listening for requests. This is not an error.*

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
