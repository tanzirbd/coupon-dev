# 🏷️ Scalable Coupon Management Platform
### Microservices Architecture — Capstone Project MSIT 5910
**Author:** Md Tanzir Altaf (C110219404) | University of the People

---

## 📌 Project Overview

A production-ready, scalable coupon management platform built with **Spring Boot microservices**, demonstrating:
- Independent service deployment and horizontal scaling
- JWT-based authentication (OAuth2-ready)
- Event-driven communication via Apache Kafka
- Redis caching for < 200 ms validation latency
- Circuit breakers via Resilience4j
- Service discovery with Netflix Eureka

---

## 🏗️ Architecture

```
                          ┌─────────────────────────────────────────┐
  Client / Admin          │           API Gateway (:8080)            │
  ─────────────  ────────►│  JWT Auth Filter │ Rate Limiting │ Route  │
                          └───────────┬─────────────────────────────┘
                                      │ (routes by path prefix)
          ┌───────────────────────────┼──────────────────────────────┐
          ▼                           ▼                              ▼
  ┌───────────────┐         ┌──────────────────┐          ┌─────────────────┐
  │ user-service  │         │ coupon-service   │          │validation-service│
  │   (:8081)     │         │   (:8082)        │          │   (:8083) ×2    │
  │  Register     │         │  CRUD Templates  │          │  Rule Engine    │
  │  Login (JWT)  │         │  Campaigns       │  ──────► │  Redis Cache    │
  │  Profiles     │         │  Kafka Publisher │          │  Kafka Publisher│
  └──────┬────────┘         └──────────┬───────┘          └────────┬────────┘
         │ PostgreSQL                  │ PostgreSQL                │ Redis
         │ (user_db)                   │ (coupon_db)               │
         └─────────────────────────────┴──────────────────────────►│
                                                                    ▼
                                                          ┌─────────────────┐
                                                          │  Apache Kafka   │
                                                          │ Topics:         │
                                                          │ coupon.created  │
                                                          │ coupon.redeemed │
                                                          │ coupon.updated  │
                                                          └────────┬────────┘
                                                      ┌────────────┴────────────┐
                                                      ▼                         ▼
                                             ┌──────────────┐        ┌──────────────────┐
                                             │notification  │        │analytics-service  │
                                             │service(:8084)│        │   (:8085)         │
                                             │ Email / SMS  │        │  Dashboards       │
                                             └──────────────┘        └──────────────────┘

                          ┌──────────────────────────────────────┐
                          │     Netflix Eureka Server (:8761)    │
                          │     Service Registry & Discovery     │
                          └──────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker Desktop

### Option A — Docker Compose (Recommended)

```bash
# 1. Clone / extract project
cd E:\Hello_world\coupon_dev

# 2. Copy Dockerfiles to each service
bash scripts/copy-dockerfiles.sh

# 3. Build and start all services
docker-compose up --build -d

# 4. Verify all services are up
docker-compose ps
```

Services will be available at:
| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8080 |
| Eureka Dashboard | http://localhost:8761 |
| User Service Swagger | http://localhost:8081/swagger-ui.html |
| Coupon Service Swagger | http://localhost:8082/swagger-ui.html |
| Validation Service Swagger | http://localhost:8083/swagger-ui.html |

### Option B — Local Dev (H2 + no Kafka)

```bash
# Start Eureka first
cd eureka-server && mvn spring-boot:run

# Then start each service with H2 profile
cd user-service && mvn spring-boot:run -Dspring.profiles.active=h2
cd coupon-service && mvn spring-boot:run -Dspring.profiles.active=h2
cd validation-service && mvn spring-boot:run
```

---

## 🔑 Core Functionality Demo

### 1. Authentication Module (User Service)

#### Register a new user
```bash
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "tanzir_admin",
  "email": "tanzir@example.com",
  "password": "SecurePass123",
  "fullName": "Tanzir Altaf",
  "role": "ADMIN"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": { "id": 1, "username": "tanzir_admin", "role": "ADMIN" }
  }
}
```

#### Login
```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "tanzir_admin",
  "password": "SecurePass123"
}
```

---

### 2. Coupon CRUD (Coupon Service)

Use the JWT token from login as `Authorization: Bearer <token>`

#### Create a coupon (ADMIN only)
```bash
POST http://localhost:8080/api/coupons
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "code": "SUMMER25",
  "description": "25% off summer sale — max discount $50",
  "type": "PERCENTAGE",
  "discountValue": 25.00,
  "minCartValue": 100.00,
  "maxDiscountCap": 50.00,
  "usageLimit": 1000,
  "perUserLimit": 1,
  "expiryDate": "2026-08-31T23:59:59"
}
```

#### List active coupons
```bash
GET http://localhost:8080/api/coupons/active?page=0&size=10
Authorization: Bearer <jwt_token>
```

#### Validate a coupon (checkout flow)
```bash
POST http://localhost:8080/api/validate
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "couponCode": "SUMMER25",
  "cartTotal": 200.00,
  "userId": "tanzir_admin"
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "valid": true,
    "discountAmount": 50.00,
    "finalCartTotal": 150.00,
    "couponType": "PERCENTAGE"
  }
}
```

---

## 📋 Requirements Traceability

| FR  | Description | Implemented In |
|-----|-------------|----------------|
| FR1 | Admin creates coupon template | `CouponController.createCoupon()` |
| FR2 | Launch distribution campaign | `CouponService` + Kafka event |
| FR3 | Issue unique coupon codes | `CouponService.createCoupon()` |
| FR4 | Validate code, return discount | `ValidationEngine.validate()` |
| FR5 | Log usage, prevent duplicates | Redis per-user redemption tracking |
| FR6 | Users view available coupons | `GET /api/coupons/active` |
| FR7 | Expiry reminders | `NotificationServiceConsumer` |
| FR8 | Analytics dashboard | `AnalyticsServiceConsumer` |

| NFR | Requirement | Design Decision |
|-----|-------------|-----------------|
| Performance | < 200 ms validation | Redis cache + gRPC-ready Feign |
| Scalability | 10x traffic spike | Kubernetes HPA; 2 validation replicas |
| Reliability | 99.95% uptime | Circuit breaker (Resilience4j) |
| Security | JWT + HTTPS | API Gateway filter + BCrypt |
| Maintainability | Independent deployments | Docker + CI/CD per service |

---

## 🛠️ Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17, Spring Boot 3.2 |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Auth | JWT (JJWT 0.12), BCrypt |
| Messaging | Apache Kafka |
| Cache | Redis 7 |
| Database | PostgreSQL 16 (per service) |
| Circuit Breaker | Resilience4j |
| Container | Docker, Docker Compose |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Build | Maven 3.9 |

---

## 📁 Project Structure

```
coupon_dev/
├── pom.xml                         ← Parent POM (multi-module)
├── docker-compose.yml              ← Full stack orchestration
├── Dockerfile.template             ← Shared multi-stage Dockerfile
├── scripts/
│   ├── copy-dockerfiles.sh         ← Deploy Dockerfiles to all services
│   └── init-multiple-dbs.sh        ← PostgreSQL multi-DB init
├── eureka-server/                  ← Service discovery (:8761)
├── api-gateway/                    ← JWT gateway + routing (:8080)
├── user-service/                   ← Auth, JWT, user profiles (:8081)
│   └── src/.../
│       ├── controller/AuthController.java
│       ├── service/AuthService.java
│       ├── security/JwtService.java
│       └── model/User.java
├── coupon-service/                 ← CRUD, Kafka publisher (:8082)
│   └── src/.../
│       ├── controller/CouponController.java
│       ├── service/CouponService.java
│       └── model/Coupon.java
├── validation-service/             ← Validation engine, Redis (:8083)
│   └── src/.../
│       ├── controller/ValidationController.java
│       ├── service/ValidationEngine.java
│       └── client/CouponServiceClient.java
├── notification-service/           ← Kafka consumer, email/SMS (:8084)
└── analytics-service/              ← Kafka consumer, metrics (:8085)
```

---
