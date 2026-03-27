# Convo Backend 
Backend service for the Convao full-stack web application built with **Spring Boot (Java)**.
This repository contains the **signalling server, WebSocket communication layer, backend configuration, and REST endpoints**.

Frontend Repository:
https://github.com/Capstone-Project-BUET-CSE-21/convo-frontend

---

# Table of Contents

* Project Overview
* Project Structure
* Notable & Important Components
* Prerequisites
* How to Run the Backend Locally
* Architecture
* Technologies Used
* Troubleshooting

---

# Project Overview

Convo backend provides the server-side functionality for the application including:

* Real-time **WebSocket signalling** for collaborative communication
* Backend configuration using **Spring Boot**
* **REST API endpoints**
* **Spring Security configuration**
* JSON utilities and credential management

The backend communicates with the React frontend through:

* HTTP REST endpoints
* WebSocket signalling messages

---

# Project Structure

```
backend/
├── mvnw
├── mvnw.cmd
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/convo/backend/
│   │   │       ├── BackendApplication.java
│   │   │       ├── config/
│   │   │       │   └── WebAndSecurityConfig.java
│   │   │       ├── controller/
│   │   │       │   └── Controller.java
│   │   │       ├── websocket/
│   │   │       │   ├── SignalingHandler.java
│   │   │       │   └── WebSocketConfig.java
│   │   │       └── utilities/
│   │   │           ├── Credentials.java
│   │   │           └── JSONUtils.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/convo/backend/
│           └── BackendApplicationTests.java
└── target/
.gitignore
README.md
```

---

# Notable & Important Components

## BackendApplication.java

Spring Boot application entry point.

Responsibilities:

* Bootstraps the Spring Boot application
* Initializes backend configuration
* Starts the embedded web server

Location:

```
src/main/java/com/convo/backend/
```

---

## WebAndSecurityConfig.java

Responsible for configuring:

* Spring Security
* CORS policies
* HTTP security settings

Location:

```
src/main/java/com/convo/backend/config/
```

---

## Controller.java

Handles backend **REST API requests**.

Example endpoint functionality:

* `/process` endpoint
* Accepts **GET** and **POST** requests
* Processes request data and returns JSON responses

Location:

```
src/main/java/com/convo/backend/controller/
```

---

## WebSocket Components

### WebSocketConfig.java

Configures:

* WebSocket endpoints
* Allowed origins
* WebSocket message broker settings

Location:

```
src/main/java/com/convo/backend/websocket/
```

---

### SignalingHandler.java

Handles real-time WebSocket communication used for signalling between connected clients.

Responsibilities:

* Receives WebSocket messages
* Broadcasts signalling data between participants
* Maintains real-time communication flow

Location:

```
src/main/java/com/convo/backend/websocket/
```

---

## JSONUtilities.java

Utility class used for:

* JSON parsing
* Data serialization
* Converting Java objects into JSON strings

Location:

```
src/main/java/com/convo/backend/utils/
```

---

## Credentials.java

Responsible for:

* Managing authentication credentials
* Validating credentials securely
* Handling credential storage logic

Location:

```
src/main/java/com/convo/backend/utils/
```

---

# Prerequisites

Before running the backend locally ensure you have:

### Java

Java 21 or higher

Check installation:

```
java -version
```

---

### Maven

Maven 3.6+

You may also use the included Maven wrapper:

```
mvnw
```

---

### Optional

* Git
* VS Code / IntelliJ

---

# How to Run the Backend Locally

### Step 1 — Clone the repository

```
git clone https://github.com/Capstone-Project-BUET-CSE-21/convo-backend
cd convo-backend
```

---

### Step 2 — Run the Spring Boot server

Using Maven wrapper:

Linux / Mac:

```
./mvnw spring-boot:run
```

Windows:

```
mvnw.cmd spring-boot:run
```

Or using installed Maven:

```
mvn spring-boot:run
```

---

### Step 3 — Verify server start

The backend will start on:

```
http://localhost:8080
```

Expected console output:

```
Started BackendApplication in X seconds
```

---

# Architecture

The backend provides two communication mechanisms:

### REST API

Frontend sends HTTP requests to backend endpoints.

Example:

```
/process
```

---

### WebSocket Signalling

Used for real-time bidirectional communication.

Flow:

1. Client connects to WebSocket endpoint
2. Signalling messages are exchanged
3. Backend relays messages between connected clients

---

# Technologies Used

| Component               | Technology      | Version         |
| ----------------------- | --------------- | --------------- |
| Backend Language        | Java            | 21              |
| Backend Framework       | Spring Boot     | 4.0.1           |
| Security                | Spring Security | Included        |
| Real-time Communication | WebSocket       | Native Java     |
| ORM                     | Hibernate / JPA | Spring Included |

---

# Troubleshooting

## Backend Won't Start

Check Java version:

```
java -version
```

Ensure port **8080** is free.

Try rebuilding:

```
mvn clean install
```

---

## WebSocket Connection Issues

Verify:

* Backend server is running
* WebSocket endpoints are configured correctly
* Frontend is connecting to the correct backend URL
