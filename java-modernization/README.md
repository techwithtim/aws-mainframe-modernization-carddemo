# CardDemo - Modernized Java 21 Application

This is a modernized Java 21 implementation of the AWS Card Demo application, refactored from the original COBOL mainframe implementation. The application provides comprehensive credit card account management functionality through a RESTful API architecture.

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Deployment](#deployment)
- [Configuration](#configuration)
- [Monitoring](#monitoring)

## Overview

CardDemo is a credit card management system that has been modernized from a COBOL/CICS/VSAM mainframe application to a cloud-native Java 21 microservice. This refactoring maintains all essential functionality while implementing modern best practices in software architecture, testing, and deployment.

### Key Features

- **Account Management**: Create, read, update, and delete credit card accounts
- **Card Management**: Manage credit cards associated with accounts
- **Transaction Processing**: Record and query credit card transactions with automatic balance updates
- **Customer Management**: Store and retrieve customer information
- **User Management**: Admin and regular user account management
- **Authentication**: Secure login and user validation
- **Bill Payment**: Pay account balances in full with automatic transaction creation
- **RESTful API**: Modern REST endpoints for all operations
- **Cloud-Ready**: Containerized and Kubernetes-ready deployment

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2.0
- **Data Access**: Spring Data JPA
- **Database**: PostgreSQL 15
- **Build Tool**: Maven 3.9+
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Containerization**: Docker
- **Orchestration**: Kubernetes
- **Database Migration**: Flyway

## Architecture

The application follows a layered architecture pattern:

```
┌─────────────────────────────────────┐
│     REST Controllers                │  ← API Layer
├─────────────────────────────────────┤
│     Service Layer                   │  ← Business Logic
├─────────────────────────────────────┤
│     Repository Layer                │  ← Data Access
├─────────────────────────────────────┤
│     PostgreSQL Database             │  ← Persistence
└─────────────────────────────────────┘
```

### Package Structure

```
com.aws.carddemo
├── controller/          # REST API endpoints
├── service/            # Business logic
├── repository/         # Data access layer
├── model/              # JPA entities
├── dto/                # Data transfer objects
├── exception/          # Exception handling
└── config/             # Configuration classes
```

## Prerequisites

- Java 21 or higher
- Maven 3.9 or higher
- Docker and Docker Compose (for containerized deployment)
- PostgreSQL 15 (if running locally without Docker)

## Getting Started

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/techwithtim/aws-mainframe-modernization-carddemo.git
   cd aws-mainframe-modernization-carddemo/java-modernization
   ```

2. **Start PostgreSQL using Docker Compose**
   ```bash
   docker-compose up -d postgres
   ```

3. **Build the application**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   The application will start on `http://localhost:8080`

### Using Docker Compose

To run the entire stack (application + database):

```bash
docker-compose up --build
```

This will start:
- PostgreSQL database on port 5432
- CardDemo application on port 8080

## API Endpoints

### Accounts API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/accounts` | Get all accounts |
| GET | `/api/v1/accounts/{id}` | Get account by ID |
| GET | `/api/v1/accounts?status={status}` | Get accounts by status |
| POST | `/api/v1/accounts` | Create new account |
| PUT | `/api/v1/accounts/{id}` | Update account |
| DELETE | `/api/v1/accounts/{id}` | Delete account |

### Cards API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/cards` | Get all cards |
| GET | `/api/v1/cards/{cardNumber}` | Get card by number |
| GET | `/api/v1/cards?accountId={id}` | Get cards by account |
| GET | `/api/v1/cards?status={status}` | Get cards by status |
| POST | `/api/v1/cards` | Create new card |
| PUT | `/api/v1/cards/{cardNumber}` | Update card |
| DELETE | `/api/v1/cards/{cardNumber}` | Delete card |

### Transactions API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/transactions` | Get all transactions |
| GET | `/api/v1/transactions/{id}` | Get transaction by ID |
| GET | `/api/v1/transactions?cardNumber={num}` | Get transactions by card |
| GET | `/api/v1/transactions?cardNumber={num}&startDate={date}&endDate={date}` | Get transactions by date range |
| POST | `/api/v1/transactions` | Create new transaction (auto-updates account balance) |
| PUT | `/api/v1/transactions/{id}` | Update transaction |
| DELETE | `/api/v1/transactions/{id}` | Delete transaction |

### Customers API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/customers` | Get all customers |
| GET | `/api/v1/customers/{id}` | Get customer by ID |
| POST | `/api/v1/customers` | Create new customer |
| PUT | `/api/v1/customers/{id}` | Update customer |
| DELETE | `/api/v1/customers/{id}` | Delete customer |

### Users API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/users` | Get all users |
| GET | `/api/v1/users/{userId}` | Get user by ID |
| GET | `/api/v1/users/type/{userType}` | Get users by type (A=Admin, R=Regular) |
| POST | `/api/v1/users` | Create new user |
| PUT | `/api/v1/users/{userId}` | Update user |
| PUT | `/api/v1/users/{userId}/password` | Update user password |
| DELETE | `/api/v1/users/{userId}` | Delete user |

### Authentication API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Authenticate user (returns user info and token) |
| POST | `/api/v1/auth/validate` | Validate user credentials |

### Bill Payment API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/billpayment` | Pay account balance in full |

### Example API Calls

**Create an Account:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 12345678901,
    "activeStatus": "Y",
    "currentBalance": 5000.00,
    "creditLimit": 10000.00,
    "cashCreditLimit": 2000.00,
    "openDate": "2024-01-01",
    "expirationDate": "2029-01-01",
    "addressZip": "12345",
    "groupId": "GRP001"
  }'
```

**Get Account by ID:**
```bash
curl http://localhost:8080/api/v1/accounts/12345678901
```

**Create a Transaction:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionTypeCode": "01",
    "transactionCategoryCode": 1001,
    "transactionSource": "POS",
    "description": "Purchase at Store",
    "amount": 150.00,
    "merchantId": 999,
    "merchantName": "Test Merchant",
    "merchantCity": "New York",
    "merchantZip": "10001",
    "cardNumber": "4111111111111111",
    "originTimestamp": "2024-01-15T10:30:00"
  }'
```

## Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests

Integration tests use Testcontainers to spin up a PostgreSQL instance:

```bash
mvn verify
```

### Test Coverage

The project includes:
- **Unit Tests**: Service and controller layer tests with Mockito
- **Integration Tests**: End-to-end API tests with Testcontainers
- **Test Coverage**: JUnit 5 with comprehensive test scenarios

## Deployment

### Docker Deployment

1. **Build the Docker image:**
   ```bash
   docker build -t carddemo:latest .
   ```

2. **Run with Docker Compose:**
   ```bash
   docker-compose up -d
   ```

### Kubernetes Deployment

1. **Apply Kubernetes manifests:**
   ```bash
   kubectl apply -f k8s/namespace.yaml
   kubectl apply -f k8s/postgres-deployment.yaml
   kubectl apply -f k8s/app-deployment.yaml
   ```

2. **Verify deployment:**
   ```bash
   kubectl get pods -n carddemo
   kubectl get services -n carddemo
   ```

3. **Access the application:**
   ```bash
   kubectl port-forward -n carddemo service/carddemo-service 8080:80
   ```

### AWS EKS Deployment

For production deployment on AWS EKS, refer to the [deployment documentation](docs/modernization.md#aws-deployment).

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/carddemo` |
| `DB_USER` | Database username | `carddemo` |
| `DB_PASS` | Database password | `carddemo` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` |

### Application Profiles

- **dev**: Development profile with debug logging
- **test**: Testing profile for integration tests
- **prod**: Production profile with optimized settings

### Database Configuration

The application uses Flyway for database migrations. Migration scripts are located in `src/main/resources/db/migration/`.

## Monitoring

### Health Checks

- **Liveness**: `http://localhost:8080/actuator/health/liveness`
- **Readiness**: `http://localhost:8080/actuator/health/readiness`
- **Full Health**: `http://localhost:8080/actuator/health`

### Metrics

Prometheus metrics are available at:
```
http://localhost:8080/actuator/prometheus
```

### Logging

Application logs are configured with different levels per profile:
- **Development**: DEBUG level for application packages
- **Production**: INFO level with structured logging

## COBOL to Java Mapping

For detailed information about how COBOL programs were mapped to Java classes, see [docs/modernization.md](docs/modernization.md).

### Key Mappings

| COBOL Program | Java Class | Functionality |
|---------------|------------|---------------|
| COACTVWC | AccountController | Account viewing |
| COACTUPC | AccountService | Account updates |
| COCRDLIC | CardController | Card listing |
| COCRDUPC | CardService | Card updates |
| COTRN02C | TransactionService | Transaction creation with balance updates |
| COSGN00C | AuthenticationController | User sign-on and authentication |
| COUSR00C-03C | UserController | User management (list, create, update, delete) |
| COBIL00C | BillPaymentController | Bill payment processing |
| CBCUS01C | CustomerController | Customer management |
| CXACAIX (file) | CardXref entity | Card-account-customer cross-reference |

## Contributing

Please read [CONTRIBUTING.md](../CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.

## Support

For questions or issues, please open an issue on GitHub or contact the development team.
