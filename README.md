# Convay - CSE 450 Capstone Project

A full-stack web application built with Spring Boot (Java) and React, featuring real-time WebSocket communication for collaborative features.

## Table of Contents

- [Project Overview](#project-overview)
- [Project Structure](#project-structure)
- [Notable & Important Components](#notable--important-components)
- [Prerequisites](#prerequisites)
- [How to Run the Project Locally](#how-to-run-the-project-locally)
- [Architecture](#architecture)
- [Technologies Used](#technologies-used)

## Project Overview

Convay is a capstone project that demonstrates a modern full-stack web application with:
- Real-time WebSocket communication for instant data updates
- Spring Boot backend with REST APIs
- React frontend with routing and component-based architecture
- Security configuration with Spring Security
- JPA/Hibernate for data persistence

## Project Structure

```
CSE450-Capstone-project-Convay/
├── README.md                          # Project documentation
├── backend/                           # Spring Boot Java Backend
│   ├── mvnw                          # Maven wrapper script (Unix)
│   ├── mvnw.cmd                      # Maven wrapper script (Windows)
│   ├── pom.xml                       # Maven project configuration
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/convay/backend/
│   │   │   │       ├── BackendApplication.java      # Spring Boot entry point
│   │   │   │       ├── Credentials.java             # Credentials handling
│   │   │   │       ├── JSON.java                    # JSON utility class
│   │   │   │       ├── config/
│   │   │   │       │   └── WebAndSecurityConfig.java # Security & Web configuration
│   │   │   │       ├── controller/
│   │   │   │       │   └── Controller.java          # REST API endpoints
│   │   │   │       └── websocket/
│   │   │   │           ├── SignalingHandler.java    # WebSocket message handler
│   │   │   │           └── WebSocketConfig.java     # WebSocket configuration
│   │   │   └── resources/
│   │   │       └── application.properties           # Application configuration
│   │   └── test/
│   │       └── java/
│   │           └── com/convay/backend/
│   │               └── BackendApplicationTests.java # Unit tests
│   └── target/                        # Compiled output and dependencies
│
└── frontend/                          # React Frontend
    ├── package.json                   # NPM dependencies and scripts
    ├── vite.config.js                # Vite bundler configuration
    ├── eslint.config.js              # ESLint code quality configuration
    ├── index.html                    # HTML entry point
    ├── src/
    │   ├── main.jsx                  # React app entry point
    │   ├── App.jsx                   # Main App component
    │   ├── App.css                   # App styling
    │   ├── index.css                 # Global styles
    │   ├── Homepage.jsx              # Homepage component
    │   ├── SingleRoom.jsx            # Room/Page component
    │   └── ...                       # Other React components
    └── public/                       # Static assets
```

## Notable & Important Components

### Backend Components

#### 1. **BackendApplication.java**
   - Spring Boot application entry point
   - Excludes default DataSource and Hibernate auto-configuration for custom setup
   - Located: `backend/src/main/java/com/convay/backend/`

#### 2. **WebAndSecurityConfig.java**
   - Configures Spring Security for authentication and authorization
   - Sets up web configuration and CORS policies
   - Located: `backend/src/main/java/com/convay/backend/config/`

#### 3. **Controller.java**
   - REST API controller handling HTTP requests
   - Provides `/process` endpoint for GET and POST requests
   - Includes data processing logic
   - Located: `backend/src/main/java/com/convay/backend/controller/`

#### 4. **WebSocket Components**
   - **WebSocketConfig.java**: Configures WebSocket endpoints and allowed origins
   - **SignalingHandler.java**: Handles real-time WebSocket messages for live communication
   - Located: `backend/src/main/java/com/convay/backend/websocket/`

#### 5. **JSON.java**
   - Utility class for JSON parsing and stringification
   - Provides helper methods for data serialization

#### 6. **Credentials.java**
   - Manages authentication credentials
   - Handles secure credential storage and validation

### Frontend Components

#### 1. **App.jsx**
   - Root component of the React application
   - Handles main layout and routing
   - Located: `frontend/src/`

#### 2. **Homepage.jsx**
   - Landing/home page component
   - Initial user interface
   - Located: `frontend/src/`

#### 3. **SingleRoom.jsx**
   - Room/collaborative page component
   - Handles WebSocket connections for real-time updates
   - Located: `frontend/src/`

#### 4. **main.jsx**
   - React DOM render entry point
   - Mounts the App component to the DOM

## Prerequisites

Before running the project locally, ensure you have the following installed:

### Backend Requirements
- **Java 21 or higher** - Download from [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or use OpenJDK
- **Maven 3.6+** - Download from [Apache Maven](https://maven.apache.org/download.cgi)
  - Or use the included Maven Wrapper (mvnw/mvnw.cmd)

### Frontend Requirements
- **Node.js 16+** - Download from [Node.js Official Site](https://nodejs.org/)
- **npm 8+** - Comes with Node.js

### Optional
- **Git** - For version control (download from [Git Official Site](https://git-scm.com/))
- **VS Code** - Recommended IDE (download from [VS Code](https://code.visualstudio.com/))

## How to Run the Project Locally

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd CSE450-Capstone-project-Convay
```

### Step 2: Run the Backend

Navigate to the backend directory:

```bash
cd backend
```

#### Option A: Using Maven Wrapper (Recommended)

**On Windows:**
```bash
mvnw.cmd spring-boot:run
```

**On Unix/Linux/Mac:**
```bash
./mvnw spring-boot:run
```

#### Option B: Using Installed Maven

```bash
mvn spring-boot:run
```

The backend server will start on `http://localhost:8080` by default.

**Expected Output:**
```
Started BackendApplication in X seconds
```

### Step 3: Run the Frontend

Open a new terminal and navigate to the frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

The frontend will be available at `http://localhost:5173` (or another port if 5173 is busy).

### Step 4: Access the Application

Open your web browser and navigate to:
```
http://localhost:5173
```

You should now see the Convay application homepage.

## Available Scripts

### Frontend Scripts

- **`npm run dev`** - Start the Vite development server with hot reload
- **`npm run build`** - Build the project for production (creates `dist/` folder)
- **`npm run preview`** - Preview the production build locally
- **`npm run lint`** - Run ESLint to check code quality

### Backend Maven Commands

- **`./mvnw spring-boot:run`** or **`mvn spring-boot:run`** - Start the Spring Boot application
- **`./mvnw clean`** or **`mvn clean`** - Remove the target directory
- **`./mvnw test`** or **`mvn test`** - Run unit tests
- **`./mvnw package`** or **`mvn package`** - Build the project into a JAR file
- **`./mvnw clean package`** or **`mvn clean package`** - Clean and build the project

## Architecture

### Technology Stack

**Backend:**
- Java 21
- Spring Boot 4.0.1
- Spring Security
- Spring Data JPA
- WebSocket (Real-time communication)
- Hibernate ORM

**Frontend:**
- React 19.2.0
- React Router 7.12.0
- Vite 7.2.4 (Build tool)
- ESLint (Code quality)

### Communication Flow

1. **REST API**: Frontend makes HTTP requests to backend endpoints (`/process`)
2. **WebSocket**: Real-time bidirectional communication for collaborative features
3. **Security**: Spring Security protects backend endpoints
4. **Data Format**: JSON for all data exchange

## Technologies Used

| Component | Technology | Version |
|-----------|-----------|---------|
| **Backend Language** | Java | 21 |
| **Backend Framework** | Spring Boot | 4.0.1 |
| **Frontend Library** | React | 19.2.0 |
| **Frontend Router** | React Router | 7.12.0 |
| **Build Tool (Frontend)** | Vite | 7.2.4 |
| **Package Manager** | npm | 8+ |
| **Code Linter** | ESLint | 9.39.1 |
| **ORM** | Hibernate/JPA | Spring included |
| **Real-time** | WebSocket | Native Java support |

## Troubleshooting

### Backend Won't Start
- Ensure Java 21 is installed: `java -version`
- Check if port 8080 is available
- Try cleaning and rebuilding: `mvn clean install`

### Frontend Won't Start
- Clear `node_modules` and reinstall: `rm -rf node_modules && npm install`
- Ensure Node.js and npm are installed correctly
- Check if port 5173 is available

### WebSocket Connection Issues
- Verify backend is running on `http://localhost:8080`
- Check browser console for connection errors
- Ensure WebSocket endpoints are properly configured in both frontend and backend

