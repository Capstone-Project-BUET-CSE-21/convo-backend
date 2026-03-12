# Convo Backend 

Backend for the CSE 450 capstone project Convo built using Spring Boot.
This server handles WebSocket signalling and backend configuration.

## Related Repository

Frontend:
https://github.com/Capstone-Project-BUET-CSE-21/convo-frontend

---

# Overview

This backend provides:

* WebSocket signalling for real-time communication
* Spring Boot server configuration
* Security and CORS configuration
* JSON utilities and credential management

---

# Project Structure

```
backend/
├── pom.xml
├── mvnw
├── src/main/java/com/convay/backend
│   ├── BackendApplication.java
│   ├── config/
│   │   └── WebAndSecurityConfig.java
│   ├── controller/
│   │   └── Controller.java
│   ├── websocket/
│   │   ├── SignalingHandler.java
│   │   └── WebSocketConfig.java
│   └── utils/
│       ├── JSONUtilities.java
│       └── Credentials.java
.gitignore
README.md
```

---

# Requirements

* Java 21+
* Maven 3.6+

---

# Running the Backend

```
cd backend
./mvnw spring-boot:run
```

Server runs at:

```
http://localhost:8080
```

---

# Key Technologies

* Java 21
* Spring Boot
* Spring Security
* WebSocket
