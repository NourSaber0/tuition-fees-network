# Architecture Update: Integration of Mock Banking Services

This document outlines the architectural changes made to consolidate the system, specifically regarding how the **Mock Banking Services** are hosted and interacted with. 

## 1. The Old Architecture (Two-Server Model)

Previously, the project relied on two separate applications that developers had to run locally:

1. **Tuition Network Backend (Java/Spring Boot):** The core application managing schools, fees, and payments, running on `http://localhost:8080`.
2. **Mock Banking Services (Python/Docker):** A standalone fake banking server used to simulate MOI validation, card payments, customer lookups, and Equal Payment Plans (EPP). This ran separately on `http://localhost:8000`.

**Drawbacks of the Old Approach:**
- Developers had to manage two separate environments (running Java alongside Python virtual environments or Docker containers).
- The end-to-end tests required ensuring both servers were running and communicating correctly over the local network.
- Documentation like `Mock Banking Services.md` instructed users to use Python 3.9+ or Docker, adding overhead to the onboarding process.

## 2. The New Architecture (Unified Spring Boot Monolith)

To streamline local development, reduce dependencies, and enforce a simpler modular monolith pattern, we **ported the Mock Banking Services entirely into the Java Spring Boot application.**

**Key Changes:**
1. **Embedded Mock Bank Module:** The Python mock banking logic has been rewritten in Java and embedded under the `src/main/java/com/tuitionnetwork/mockbank/` package.
2. **Single Server Execution:** When you start the Spring Boot application, it now binds to `http://localhost:8080` and serves **both** the Tuition Network APIs and the Mock Banking Services APIs simultaneously. You no longer need to run the Python server or Docker container.
3. **Internal Adapter (`MockBankAdapterImpl`):** The actual Tuition Network payment engine (in `com.tuitionnetwork.payments`) does not make HTTP network calls to charge cards during testing. Instead, it uses an internal adapter that instantly simulates a successful charge (generating a fake `AUTH-` code) to avoid HTTP timeouts and keep database transactions fast.
4. **Archived Python Tests:** The old test files for the standalone Python server were moved to `python_tests/archive_mock_bank/`. They technically still pass against the Java port since the API contracts (like `/api/v1/customers`) were preserved perfectly.

## 3. How to Test the New Architecture

Because everything is unified in Java, running the tests is much simpler:

- **Primary Backend Tests:** 
  You can run `pytest python_tests/test_actual_backend.py` to test the live Spring Boot backend. It exercises the entire Tuition Network ecosystem (authentication, RBAC, enrollment, payment idempotency) without needing a separate bank server.
- **Java Integration Tests:**
  Run `./mvnw clean test` to execute the comprehensive Spring Boot test suites.

## Summary of Benefits
- **Zero Python/Docker Dependency:** New developers only need Java 17+ and Maven to run the entire project.
- **Faster Tests:** Internal Java mocking for bank transactions is significantly faster than establishing HTTP connections to a Python server.
- **Simplified Deployment:** Only one artifact (the Spring Boot JAR) needs to be built and managed.
