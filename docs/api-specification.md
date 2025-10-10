# REST API Specification

## Table of Contents

1. [Introduction](#introduction)
2. [OpenAPI 3.0 Specification](#openapi-30-specification)
3. [Authentication](#authentication)
4. [Account Endpoints](#account-endpoints)
5. [Card Endpoints](#card-endpoints)
6. [Transaction Endpoints](#transaction-endpoints)
7. [Payment Endpoints](#payment-endpoints)
8. [User Administration Endpoints](#user-administration-endpoints)
9. [Menu Endpoints](#menu-endpoints)
10. [Report Endpoints](#report-endpoints)
11. [Common Schemas](#common-schemas)
12. [HTTP Status Codes](#http-status-codes)
13. [Error Handling](#error-handling)
14. [Pagination](#pagination)
15. [BMS Screen to REST API Mapping](#bms-screen-to-rest-api-mapping)

---

## Introduction

This document provides comprehensive REST API documentation for the modernized AWS CardDemo application. The API exposes all functionality from the legacy COBOL/CICS application through modern RESTful endpoints, maintaining complete functional equivalence while adopting contemporary HTTP-based interfaces.

**Base URL**: `http://localhost:8080/api/v1`

**Production URL**: `https://carddemo.example.com/api/v1`

**API Version**: 1.0.0

**Authentication**: JWT Bearer Token (required for all endpoints except login)

**Content Type**: `application/json`

**Character Encoding**: UTF-8

---

## OpenAPI 3.0 Specification

Below is a condensed OpenAPI 3.0 specification for the CardDemo API. The full specification is available via Swagger UI at `/swagger-ui.html` when the application is running.

```yaml
openapi: 3.0.3
info:
  title: CardDemo Modernized API
  description: RESTful API for credit card management system migrated from COBOL to Java 21
  version: 1.0.0
  contact:
    name: CardDemo Support
    email: support@carddemo.example.com
  license:
    name: Apache 2.0
    url: https://www.apache.org/licenses/LICENSE-2.0

servers:
  - url: http://localhost:8080/api/v1
    description: Local development server
  - url: https://carddemo.example.com/api/v1
    description: Production server

security:
  - bearerAuth: []

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    Account:
      type: object
      properties:
        accountId:
          type: integer
          format: int64
          example: 1234567890
        accountNumber:
          type: string
          pattern: '^[0-9]{11}$'
          example: "00000000001"
        accountStatus:
          type: string
          enum: [ACTIVE, CLOSED, SUSPENDED]
          example: "ACTIVE"
        currentBalance:
          type: number
          format: double
          example: 1234.56
        creditLimit:
          type: number
          format: double
          example: 10000.00
        cashAdvanceLimit:
          type: number
          format: double
          example: 5000.00

    Card:
      type: object
      properties:
        cardId:
          type: integer
          format: int64
        cardNumber:
          type: string
          pattern: '^[0-9]{16}$'
          example: "4111111111111111"
        cardStatus:
          type: string
          enum: [ACTIVE, BLOCKED, EXPIRED]
        expirationDate:
          type: string
          format: date
          example: "2025-12-31"

    Transaction:
      type: object
      properties:
        transactionId:
          type: string
          maxLength: 16
          example: "TXN000000000001"
        transactionDate:
          type: string
          format: date
          example: "2024-01-15"
        description:
          type: string
          maxLength: 26
          example: "AMAZON PURCHASE"
        amount:
          type: number
          format: double
          example: 49.99

    Error:
      type: object
      properties:
        timestamp:
          type: string
          format: date-time
        status:
          type: integer
        error:
          type: string
        message:
          type: string
        path:
          type: string

paths:
  /auth/login:
    post:
      summary: Authenticate user
      tags: [Authentication]
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
      responses:
        '200':
          description: Authentication successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        '401':
          description: Invalid credentials

  /accounts/{id}:
    get:
      summary: Get account details
      tags: [Accounts]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Account details retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AccountResponse'
        '404':
          description: Account not found
```

---

## Authentication

### POST /api/v1/auth/login

Authenticate a user and receive a JWT access token.

**Replaces**: COSGN00.bms (Login Screen)

**Security**: None (public endpoint)

**Request Body**:
```json
{
  "username": "USER0001",
  "password": "password"
}
```

**Request Schema**:
| Field | Type | Required | Max Length | Description |
|-------|------|----------|------------|-------------|
| username | string | Yes | 8 | User ID (maps to USERID field in COSGN00) |
| password | string | Yes | 8 | User password (maps to PASSWD field in COSGN00) |

**Success Response** (200 OK):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "issuedAt": "2024-01-15T10:30:00Z",
  "user": {
    "userId": 1,
    "username": "USER0001",
    "firstName": "John",
    "lastName": "Doe",
    "userType": "REGULAR",
    "roles": ["ROLE_USER"]
  }
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| accessToken | string | JWT token for subsequent API calls |
| tokenType | string | Always "Bearer" |
| expiresIn | integer | Token expiration time in seconds (3600 = 1 hour) |
| issuedAt | string (ISO 8601) | Token issue timestamp |
| user | object | Authenticated user information |
| user.userId | integer | Internal user ID |
| user.username | string | Username |
| user.firstName | string | User's first name |
| user.lastName | string | User's last name |
| user.userType | string | User type (REGULAR or ADMIN) |
| user.roles | array | User roles for authorization |

**Error Responses**:

401 Unauthorized - Invalid credentials:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "path": "/api/v1/auth/login"
}
```

400 Bad Request - Missing required fields:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: username is required",
  "path": "/api/v1/auth/login"
}
```

**Usage Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "USER0001",
    "password": "password"
  }'
```

### POST /api/v1/auth/logout

Invalidate the current JWT token and end the user session.

**Security**: Requires valid JWT token

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (204 No Content):
```
(Empty response body)
```

**Error Responses**:

401 Unauthorized - Invalid or expired token:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/v1/auth/logout"
}
```

---

## Account Endpoints

### GET /api/v1/accounts/{id}

Retrieve detailed information for a specific account.

**Replaces**: COACTVW.bms (Account View Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Account ID (maps to ACCTSID field in COACTVW) |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "accountId": 1234567890,
  "accountNumber": "00000000001",
  "accountStatus": "ACTIVE",
  "currentBalance": 1234.56,
  "availableCredit": 8765.44,
  "creditLimit": 10000.00,
  "cashAdvanceLimit": 5000.00,
  "openDate": "2020-01-15",
  "expirationDate": "2025-12-31",
  "reissueDate": "2023-01-15",
  "cycleBalance": 1200.00,
  "pastDueAmount": 0.00,
  "lastStatementDate": "2024-01-01",
  "lastPaymentDate": "2024-01-10",
  "lastPaymentAmount": 150.00,
  "customer": {
    "customerId": 100001,
    "firstName": "John",
    "middleName": "A",
    "lastName": "Doe",
    "addressLine1": "123 Main Street",
    "addressLine2": "Apt 4B",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "USA",
    "phoneNumber1": "2125551234",
    "phoneNumber2": "2125555678",
    "ssn": "***-**-1234",
    "governmentId": "DL12345678",
    "dateOfBirth": "1980-05-15",
    "creditScore": 750,
    "firstName": "JOHN",
    "lastName": "DOE"
  }
}
```

**Response Schema**:
| Field | Type | Description | Maps to BMS Field |
|-------|------|-------------|-------------------|
| accountId | integer | Internal account ID | - |
| accountNumber | string (11) | Account number | ACCTNO |
| accountStatus | string | Account status | ACSTATUS |
| currentBalance | number | Current account balance | ACURBAL |
| availableCredit | number | Available credit | ACRDLIM - ACURBAL |
| creditLimit | number | Credit limit | ACRDLIM |
| cashAdvanceLimit | number | Cash advance limit | ACSHLIM |
| openDate | string (date) | Account open date | AOPNDATE |
| expirationDate | string (date) | Account expiration | AEXPDATE |
| reissueDate | string (date) | Last reissue date | AREISDT |
| cycleBalance | number | Cycle balance | ACYCBAL |
| pastDueAmount | number | Past due amount | APAYDUE |
| lastStatementDate | string (date) | Last statement date | ASTMTDT |
| lastPaymentDate | string (date) | Last payment date | ALASTPDT |
| lastPaymentAmount | number | Last payment amount | ALASTPAY |
| customer | object | Customer information | - |
| customer.customerId | integer | Customer ID | CUSTID |
| customer.firstName | string | First name | ACFNAME |
| customer.middleName | string | Middle name | ACMNAME |
| customer.lastName | string | Last name | ACLNAME |
| customer.addressLine1 | string | Address line 1 | ACADL1 |
| customer.addressLine2 | string | Address line 2 | ACADL2 |
| customer.city | string | City | ACCITY |
| customer.state | string | State code | ACSTATE |
| customer.zipCode | string | ZIP code | ACPOSTAL |
| customer.country | string | Country | ACCOUNTY |
| customer.phoneNumber1 | string | Primary phone | ACPH1 |
| customer.phoneNumber2 | string | Secondary phone | ACPH2 |
| customer.ssn | string | SSN (masked) | - |
| customer.governmentId | string | Government ID | - |
| customer.dateOfBirth | string (date) | Date of birth | - |
| customer.creditScore | integer | Credit score | - |

**Error Responses**:

404 Not Found - Account doesn't exist:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID 1234567890 not found",
  "path": "/api/v1/accounts/1234567890"
}
```

401 Unauthorized - Missing or invalid token:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/v1/accounts/1234567890"
}
```

**Usage Example**:
```bash
curl -X GET http://localhost:8080/api/v1/accounts/1234567890 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### PUT /api/v1/accounts/{id}

Update account information.

**Replaces**: COACTUP.bms (Account Update Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Account ID to update |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body**:
```json
{
  "accountStatus": "ACTIVE",
  "creditLimit": 15000.00,
  "cashAdvanceLimit": 7500.00,
  "expirationDate": "2026-12-31",
  "customer": {
    "firstName": "John",
    "middleName": "A",
    "lastName": "Doe",
    "addressLine1": "456 Oak Avenue",
    "addressLine2": "Suite 200",
    "city": "Boston",
    "state": "MA",
    "zipCode": "02101",
    "country": "USA",
    "phoneNumber1": "6175551234",
    "phoneNumber2": ""
  }
}
```

**Request Schema**:
| Field | Type | Required | Validation | Maps to BMS Field |
|-------|------|----------|------------|-------------------|
| accountStatus | string | No | Enum: ACTIVE, CLOSED, SUSPENDED | ACSTTUS |
| creditLimit | number | No | Min: 0, Max: 999999.99 | ACRDLIM |
| cashAdvanceLimit | number | No | Min: 0, Max: 999999.99 | ACSHLIM |
| expirationDate | string (date) | No | Format: YYYY-MM-DD, Future date | ACEXPDT |
| customer | object | No | Customer details | - |
| customer.firstName | string | No | Max: 25 characters | ACSFNAM |
| customer.middleName | string | No | Max: 25 characters | ACSMNAME |
| customer.lastName | string | No | Max: 25 characters | ACSLNAM |
| customer.addressLine1 | string | No | Max: 50 characters | ACSADL1 |
| customer.addressLine2 | string | No | Max: 50 characters | ACSADL2 |
| customer.city | string | No | Max: 50 characters | ACSCITY |
| customer.state | string | No | 2-letter state code | ACSSTAT |
| customer.zipCode | string | No | 5 or 9 digits | ACSZIP |
| customer.country | string | No | Max: 50 characters | ACSCOUN |
| customer.phoneNumber1 | string | No | 10 digits | ACSPH1 |
| customer.phoneNumber2 | string | No | 10 digits (optional) | ACSPH2 |

**Success Response** (200 OK):
```json
{
  "accountId": 1234567890,
  "accountNumber": "00000000001",
  "accountStatus": "ACTIVE",
  "currentBalance": 1234.56,
  "creditLimit": 15000.00,
  "cashAdvanceLimit": 7500.00,
  "expirationDate": "2026-12-31",
  "customer": {
    "customerId": 100001,
    "firstName": "John",
    "middleName": "A",
    "lastName": "Doe",
    "addressLine1": "456 Oak Avenue",
    "addressLine2": "Suite 200",
    "city": "Boston",
    "state": "MA",
    "zipCode": "02101",
    "country": "USA",
    "phoneNumber1": "6175551234",
    "phoneNumber2": ""
  },
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

**Error Responses**:

400 Bad Request - Validation error:
```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: creditLimit must be greater than or equal to 0",
  "path": "/api/v1/accounts/1234567890"
}
```

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/accounts/1234567890"
}
```

404 Not Found - Account doesn't exist:
```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID 1234567890 not found",
  "path": "/api/v1/accounts/1234567890"
}
```

**Usage Example**:
```bash
curl -X PUT http://localhost:8080/api/v1/accounts/1234567890 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "creditLimit": 15000.00,
    "cashAdvanceLimit": 7500.00
  }'
```

---

## Card Endpoints

### GET /api/v1/accounts/{accountId}/cards

List all cards associated with an account.

**Replaces**: COCRDLI.bms (Card List Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| accountId | integer | Yes | Account ID to retrieve cards for (maps to ACCTSID in COCRDLI) |

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| cardNumber | string | No | - | Filter by card number (partial match, maps to CARDSID) |
| page | integer | No | 0 | Page number (zero-indexed) |
| size | integer | No | 10 | Page size (max 100) |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "cards": [
    {
      "cardId": 1,
      "cardNumber": "4111111111111111",
      "accountNumber": "00000000001",
      "cardStatus": "ACTIVE",
      "cardType": "VISA",
      "expirationDate": "2025-12-31",
      "issueDate": "2020-01-15",
      "cardholderName": "JOHN DOE"
    },
    {
      "cardId": 2,
      "cardNumber": "4111111111112222",
      "accountNumber": "00000000001",
      "cardStatus": "BLOCKED",
      "cardType": "VISA",
      "expirationDate": "2024-06-30",
      "issueDate": "2019-07-01",
      "cardholderName": "JOHN DOE"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Response Schema**:
| Field | Type | Description | Maps to BMS Field |
|-------|------|-------------|-------------------|
| cards | array | List of cards | - |
| cards[].cardId | integer | Internal card ID | - |
| cards[].cardNumber | string (16) | Card number (partially masked in UI) | CRDNUM1-7 |
| cards[].accountNumber | string (11) | Associated account number | ACCTNO1-7 |
| cards[].cardStatus | string | Card status (ACTIVE, BLOCKED, EXPIRED) | CRDSTS1-7 |
| cards[].cardType | string | Card type (VISA, MASTERCARD, etc.) | - |
| cards[].expirationDate | string (date) | Expiration date | - |
| cards[].issueDate | string (date) | Issue date | - |
| cards[].cardholderName | string | Name on card | - |
| pagination | object | Pagination metadata | - |
| pagination.page | integer | Current page number | PAGENO |
| pagination.size | integer | Page size | - |
| pagination.totalElements | integer | Total number of cards | - |
| pagination.totalPages | integer | Total number of pages | - |
| pagination.first | boolean | Is first page | - |
| pagination.last | boolean | Is last page | - |

**Error Responses**:

404 Not Found - Account doesn't exist:
```json
{
  "timestamp": "2024-01-15T10:40:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID 1234567890 not found",
  "path": "/api/v1/accounts/1234567890/cards"
}
```

**Usage Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/accounts/1234567890/cards?page=0&size=10" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### GET /api/v1/cards/{cardNumber}

Retrieve detailed information for a specific card.

**Replaces**: COCRDSL.bms (Card Select/Detail Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| cardNumber | string | Yes | 16-digit card number |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "cardId": 1,
  "cardNumber": "4111111111111111",
  "accountId": 1234567890,
  "accountNumber": "00000000001",
  "cardStatus": "ACTIVE",
  "cardType": "VISA",
  "expirationDate": "2025-12-31",
  "issueDate": "2020-01-15",
  "cardholderName": "JOHN DOE",
  "embossedName": "JOHN A DOE",
  "creditLimit": 10000.00,
  "availableCredit": 8765.44,
  "currentBalance": 1234.56
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| cardId | integer | Internal card ID |
| cardNumber | string (16) | Full card number |
| accountId | integer | Associated account ID |
| accountNumber | string (11) | Associated account number |
| cardStatus | string | Card status (ACTIVE, BLOCKED, EXPIRED) |
| cardType | string | Card brand (VISA, MASTERCARD, AMEX, DISCOVER) |
| expirationDate | string (date) | Expiration date |
| issueDate | string (date) | Issue date |
| cardholderName | string | Name on card |
| embossedName | string | Embossed name |
| creditLimit | number | Credit limit for this card |
| availableCredit | number | Available credit |
| currentBalance | number | Current balance |

**Error Responses**:

404 Not Found - Card doesn't exist:
```json
{
  "timestamp": "2024-01-15T10:45:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Card with number 4111111111111111 not found",
  "path": "/api/v1/cards/4111111111111111"
}
```

**Usage Example**:
```bash
curl -X GET http://localhost:8080/api/v1/cards/4111111111111111 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### PUT /api/v1/cards/{id}

Update card information.

**Replaces**: COCRDUP.bms (Card Update Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Card ID to update |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body**:
```json
{
  "cardStatus": "BLOCKED",
  "expirationDate": "2026-12-31",
  "cardholderName": "JOHN A DOE"
}
```

**Request Schema**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| cardStatus | string | No | Enum: ACTIVE, BLOCKED, EXPIRED | New card status |
| expirationDate | string (date) | No | Future date | New expiration date |
| cardholderName | string | No | Max: 50 characters | Updated cardholder name |

**Success Response** (200 OK):
```json
{
  "cardId": 1,
  "cardNumber": "4111111111111111",
  "accountNumber": "00000000001",
  "cardStatus": "BLOCKED",
  "expirationDate": "2026-12-31",
  "cardholderName": "JOHN A DOE",
  "updatedAt": "2024-01-15T10:50:00Z"
}
```

**Error Responses**:

400 Bad Request - Validation error:
```json
{
  "timestamp": "2024-01-15T10:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: Invalid card status",
  "path": "/api/v1/cards/1"
}
```

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T10:50:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/cards/1"
}
```

404 Not Found - Card doesn't exist:
```json
{
  "timestamp": "2024-01-15T10:50:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Card with ID 1 not found",
  "path": "/api/v1/cards/1"
}
```

**Usage Example**:
```bash
curl -X PUT http://localhost:8080/api/v1/cards/1 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "cardStatus": "BLOCKED"
  }'
```

---

## Transaction Endpoints

### GET /api/v1/accounts/{accountId}/transactions

List transactions for a specific account with pagination.

**Replaces**: COTRN00.bms (Transaction List Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| accountId | integer | Yes | Account ID to retrieve transactions for (maps to ACCTSID in COTRN00) |

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| cardNumber | string | No | - | Filter by card number (maps to CARDSID) |
| startDate | string (date) | No | - | Start date (YYYY-MM-DD) |
| endDate | string (date) | No | - | End date (YYYY-MM-DD) |
| page | integer | No | 0 | Page number (zero-indexed, maps to PAGENO) |
| size | integer | No | 10 | Page size (max 100) |
| sort | string | No | transactionDate,desc | Sort criteria (field,direction) |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "transactions": [
    {
      "transactionId": "TXN000000000001",
      "accountId": 1234567890,
      "cardNumber": "4111111111111111",
      "transactionDate": "2024-01-15",
      "transactionTime": "14:30:00",
      "transactionType": "PURCHASE",
      "transactionCategory": "RETAIL",
      "description": "AMAZON PURCHASE",
      "amount": 49.99,
      "merchantName": "Amazon.com",
      "merchantCity": "Seattle",
      "merchantState": "WA",
      "merchantZip": "98101",
      "originalAmount": 49.99,
      "referenceNumber": "REF123456789",
      "confirmationNumber": "CONF987654321"
    },
    {
      "transactionId": "TXN000000000002",
      "accountId": 1234567890,
      "cardNumber": "4111111111111111",
      "transactionDate": "2024-01-14",
      "transactionTime": "09:15:00",
      "transactionType": "PAYMENT",
      "transactionCategory": "PAYMENT",
      "description": "ONLINE PAYMENT",
      "amount": -150.00,
      "merchantName": "PAYMENT",
      "merchantCity": "",
      "merchantState": "",
      "merchantZip": "",
      "originalAmount": -150.00,
      "referenceNumber": "REF123456788",
      "confirmationNumber": "CONF987654320"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Response Schema**:
| Field | Type | Description | Maps to BMS Field |
|-------|------|-------------|-------------------|
| transactions | array | List of transactions | - |
| transactions[].transactionId | string (16) | Transaction ID | TRNID01-10 |
| transactions[].accountId | integer | Account ID | - |
| transactions[].cardNumber | string (16) | Card number (masked in display) | - |
| transactions[].transactionDate | string (date) | Transaction date | TDATE01-10 |
| transactions[].transactionTime | string (time) | Transaction time (HH:MM:SS) | - |
| transactions[].transactionType | string | Transaction type code | - |
| transactions[].transactionCategory | string | Transaction category | - |
| transactions[].description | string (26) | Transaction description | TDESC01-10 |
| transactions[].amount | number | Transaction amount | TAMT001-010 |
| transactions[].merchantName | string | Merchant name | - |
| transactions[].merchantCity | string | Merchant city | - |
| transactions[].merchantState | string | Merchant state | - |
| transactions[].merchantZip | string | Merchant ZIP | - |
| transactions[].originalAmount | number | Original amount (before conversions) | - |
| transactions[].referenceNumber | string | Reference number | - |
| transactions[].confirmationNumber | string | Confirmation number | - |
| pagination | object | Pagination metadata | - |
| pagination.page | integer | Current page number | PAGENO |
| pagination.size | integer | Page size | - |
| pagination.totalElements | integer | Total transactions | - |
| pagination.totalPages | integer | Total pages | - |
| pagination.first | boolean | Is first page | - |
| pagination.last | boolean | Is last page | - |

**Error Responses**:

404 Not Found - Account doesn't exist:
```json
{
  "timestamp": "2024-01-15T11:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID 1234567890 not found",
  "path": "/api/v1/accounts/1234567890/transactions"
}
```

400 Bad Request - Invalid date format:
```json
{
  "timestamp": "2024-01-15T11:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid date format. Use YYYY-MM-DD",
  "path": "/api/v1/accounts/1234567890/transactions"
}
```

**Usage Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/accounts/1234567890/transactions?page=0&size=10&sort=transactionDate,desc" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### GET /api/v1/transactions/{id}

Retrieve detailed information for a specific transaction.

**Replaces**: COTRN01.bms (Transaction Detail Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | string | Yes | Transaction ID (16 characters) |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "transactionId": "TXN000000000001",
  "accountId": 1234567890,
  "accountNumber": "00000000001",
  "cardNumber": "4111111111111111",
  "transactionDate": "2024-01-15",
  "transactionTime": "14:30:00",
  "transactionType": "PURCHASE",
  "transactionTypeDescription": "Purchase",
  "transactionCategory": "RETAIL",
  "transactionCategoryDescription": "Retail Purchase",
  "description": "AMAZON PURCHASE",
  "amount": 49.99,
  "merchantName": "Amazon.com",
  "merchantStreet": "123 Commerce St",
  "merchantCity": "Seattle",
  "merchantState": "WA",
  "merchantZip": "98101",
  "originalAmount": 49.99,
  "originalCurrency": "USD",
  "exchangeRate": 1.0,
  "referenceNumber": "REF123456789",
  "confirmationNumber": "CONF987654321",
  "authorizationCode": "AUTH12345",
  "processingDate": "2024-01-15",
  "postingDate": "2024-01-16",
  "status": "POSTED"
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| transactionId | string (16) | Unique transaction ID |
| accountId | integer | Account ID |
| accountNumber | string (11) | Account number |
| cardNumber | string (16) | Card number (masked for display) |
| transactionDate | string (date) | Transaction date |
| transactionTime | string (time) | Transaction time |
| transactionType | string | Transaction type code |
| transactionTypeDescription | string | Transaction type description |
| transactionCategory | string | Transaction category code |
| transactionCategoryDescription | string | Transaction category description |
| description | string | Transaction description |
| amount | number | Transaction amount |
| merchantName | string | Merchant name |
| merchantStreet | string | Merchant street address |
| merchantCity | string | Merchant city |
| merchantState | string | Merchant state |
| merchantZip | string | Merchant ZIP code |
| originalAmount | number | Original amount (if foreign currency) |
| originalCurrency | string | Original currency code |
| exchangeRate | number | Exchange rate applied |
| referenceNumber | string | Reference number |
| confirmationNumber | string | Confirmation number |
| authorizationCode | string | Authorization code |
| processingDate | string (date) | Processing date |
| postingDate | string (date) | Posting date |
| status | string | Transaction status (PENDING, POSTED, REVERSED) |

**Error Responses**:

404 Not Found - Transaction doesn't exist:
```json
{
  "timestamp": "2024-01-15T11:05:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Transaction with ID TXN000000000001 not found",
  "path": "/api/v1/transactions/TXN000000000001"
}
```

**Usage Example**:
```bash
curl -X GET http://localhost:8080/api/v1/transactions/TXN000000000001 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### POST /api/v1/transactions

Create a new manual transaction entry.

**Replaces**: COTRN02.bms (Transaction Add Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body**:
```json
{
  "accountId": 1234567890,
  "cardNumber": "4111111111111111",
  "transactionDate": "2024-01-15",
  "transactionType": "PURCHASE",
  "transactionCategory": "RETAIL",
  "description": "MANUAL ENTRY - STORE PURCHASE",
  "amount": 125.50,
  "merchantName": "Local Store",
  "merchantCity": "New York",
  "merchantState": "NY",
  "merchantZip": "10001",
  "referenceNumber": "REF987654321"
}
```

**Request Schema**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| accountId | integer | Yes | Must exist | Account ID |
| cardNumber | string (16) | Yes | Must exist, belong to account | Card number |
| transactionDate | string (date) | Yes | YYYY-MM-DD, not future | Transaction date |
| transactionType | string | Yes | Valid transaction type code | Transaction type |
| transactionCategory | string | Yes | Valid category code | Transaction category |
| description | string | Yes | Max: 26 characters | Transaction description |
| amount | number | Yes | Min: 0.01, Max: 999999.99 | Transaction amount |
| merchantName | string | No | Max: 50 characters | Merchant name |
| merchantCity | string | No | Max: 50 characters | Merchant city |
| merchantState | string | No | 2-letter state code | Merchant state |
| merchantZip | string | No | 5 digits | Merchant ZIP |
| referenceNumber | string | No | Max: 16 characters | Reference number |

**Success Response** (201 Created):
```json
{
  "transactionId": "TXN000000000999",
  "accountId": 1234567890,
  "cardNumber": "4111111111111111",
  "transactionDate": "2024-01-15",
  "transactionType": "PURCHASE",
  "transactionCategory": "RETAIL",
  "description": "MANUAL ENTRY - STORE PURCHASE",
  "amount": 125.50,
  "merchantName": "Local Store",
  "merchantCity": "New York",
  "merchantState": "NY",
  "merchantZip": "10001",
  "referenceNumber": "REF987654321",
  "status": "POSTED",
  "createdAt": "2024-01-15T11:10:00Z"
}
```

**Response Headers**:
```
Location: /api/v1/transactions/TXN000000000999
```

**Error Responses**:

400 Bad Request - Validation error:
```json
{
  "timestamp": "2024-01-15T11:10:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: amount must be greater than 0",
  "path": "/api/v1/transactions"
}
```

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:10:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/transactions"
}
```

404 Not Found - Account or card not found:
```json
{
  "timestamp": "2024-01-15T11:10:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID 1234567890 not found",
  "path": "/api/v1/transactions"
}
```

409 Conflict - Card doesn't belong to account:
```json
{
  "timestamp": "2024-01-15T11:10:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Card 4111111111111111 does not belong to account 1234567890",
  "path": "/api/v1/transactions"
}
```

**Usage Example**:
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 1234567890,
    "cardNumber": "4111111111111111",
    "transactionDate": "2024-01-15",
    "transactionType": "PURCHASE",
    "transactionCategory": "RETAIL",
    "description": "MANUAL ENTRY",
    "amount": 125.50
  }'
```

---

## Payment Endpoints

### POST /api/v1/accounts/{accountId}/payments

Process a bill payment for an account.

**Replaces**: COBIL00.bms (Bill Payment Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| accountId | integer | Yes | Account ID to process payment for |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body**:
```json
{
  "paymentAmount": 150.00,
  "paymentDate": "2024-01-15",
  "paymentMethod": "ACH",
  "bankAccountNumber": "****1234",
  "routingNumber": "021000021",
  "confirmationEmail": "john.doe@example.com"
}
```

**Request Schema**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| paymentAmount | number | Yes | Min: 0.01, Max: currentBalance | Payment amount |
| paymentDate | string (date) | Yes | YYYY-MM-DD, today or future | Payment date |
| paymentMethod | string | Yes | Enum: ACH, WIRE, CHECK | Payment method |
| bankAccountNumber | string | Yes (if ACH/WIRE) | 4-17 digits (masked) | Bank account number |
| routingNumber | string | Yes (if ACH/WIRE) | 9 digits | Bank routing number |
| confirmationEmail | string | No | Valid email format | Email for confirmation |

**Success Response** (201 Created):
```json
{
  "paymentId": "PAY000000001",
  "accountId": 1234567890,
  "paymentAmount": 150.00,
  "paymentDate": "2024-01-15",
  "paymentMethod": "ACH",
  "confirmationNumber": "CONF123456789",
  "status": "PENDING",
  "previousBalance": 1234.56,
  "newBalance": 1084.56,
  "processedAt": "2024-01-15T11:15:00Z"
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| paymentId | string | Unique payment ID |
| accountId | integer | Account ID |
| paymentAmount | number | Payment amount |
| paymentDate | string (date) | Scheduled payment date |
| paymentMethod | string | Payment method |
| confirmationNumber | string | Payment confirmation number |
| status | string | Payment status (PENDING, PROCESSED, FAILED) |
| previousBalance | number | Balance before payment |
| newBalance | number | Balance after payment |
| processedAt | string (ISO 8601) | Processing timestamp |

**Error Responses**:

400 Bad Request - Validation error:
```json
{
  "timestamp": "2024-01-15T11:15:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: paymentAmount exceeds current balance",
  "path": "/api/v1/accounts/1234567890/payments"
}
```

404 Not Found - Account doesn't exist:
```json
{
  "timestamp": "2024-01-15T11:15:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID 1234567890 not found",
  "path": "/api/v1/accounts/1234567890/payments"
}
```

422 Unprocessable Entity - Insufficient funds:
```json
{
  "timestamp": "2024-01-15T11:15:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Insufficient funds in bank account",
  "path": "/api/v1/accounts/1234567890/payments"
}
```

**Usage Example**:
```bash
curl -X POST http://localhost:8080/api/v1/accounts/1234567890/payments \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "paymentAmount": 150.00,
    "paymentDate": "2024-01-15",
    "paymentMethod": "ACH",
    "bankAccountNumber": "****1234",
    "routingNumber": "021000021"
  }'
```

---

## User Administration Endpoints

### GET /api/v1/admin/users

List all users in the system (admin only).

**Replaces**: COUSR00.bms (User List Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| userType | string | No | - | Filter by user type (REGULAR, ADMIN) |
| page | integer | No | 0 | Page number (zero-indexed) |
| size | integer | No | 10 | Page size (max 100) |
| sort | string | No | username,asc | Sort criteria |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "users": [
    {
      "userId": 1,
      "username": "USER0001",
      "firstName": "John",
      "lastName": "Doe",
      "userType": "REGULAR",
      "status": "ACTIVE",
      "createdAt": "2020-01-15T00:00:00Z",
      "lastLoginAt": "2024-01-15T10:00:00Z"
    },
    {
      "userId": 2,
      "username": "ADMIN001",
      "firstName": "Jane",
      "lastName": "Smith",
      "userType": "ADMIN",
      "status": "ACTIVE",
      "createdAt": "2020-01-15T00:00:00Z",
      "lastLoginAt": "2024-01-14T15:30:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| users | array | List of users |
| users[].userId | integer | Internal user ID |
| users[].username | string (8) | Username |
| users[].firstName | string | First name |
| users[].lastName | string | Last name |
| users[].userType | string | User type (REGULAR, ADMIN) |
| users[].status | string | User status (ACTIVE, INACTIVE, LOCKED) |
| users[].createdAt | string (ISO 8601) | Account creation timestamp |
| users[].lastLoginAt | string (ISO 8601) | Last login timestamp |
| pagination | object | Pagination metadata |

**Error Responses**:

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:20:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/users"
}
```

**Usage Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/admin/users?page=0&size=10" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### POST /api/v1/admin/users

Create a new user account (admin only).

**Replaces**: COUSR01.bms (User Add Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body**:
```json
{
  "username": "USER0003",
  "password": "TempPass123!",
  "firstName": "Alice",
  "lastName": "Johnson",
  "userType": "REGULAR"
}
```

**Request Schema**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| username | string | Yes | 8 characters, alphanumeric | Username |
| password | string | Yes | 8 characters minimum, mixed case + digits | Password |
| firstName | string | Yes | Max: 25 characters | First name |
| lastName | string | Yes | Max: 25 characters | Last name |
| userType | string | Yes | Enum: REGULAR, ADMIN | User type |

**Success Response** (201 Created):
```json
{
  "userId": 3,
  "username": "USER0003",
  "firstName": "Alice",
  "lastName": "Johnson",
  "userType": "REGULAR",
  "status": "ACTIVE",
  "createdAt": "2024-01-15T11:25:00Z"
}
```

**Response Headers**:
```
Location: /api/v1/admin/users/3
```

**Error Responses**:

400 Bad Request - Validation error:
```json
{
  "timestamp": "2024-01-15T11:25:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: password must be at least 8 characters",
  "path": "/api/v1/admin/users"
}
```

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:25:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/users"
}
```

409 Conflict - Username already exists:
```json
{
  "timestamp": "2024-01-15T11:25:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Username USER0003 already exists",
  "path": "/api/v1/admin/users"
}
```

**Usage Example**:
```bash
curl -X POST http://localhost:8080/api/v1/admin/users \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "username": "USER0003",
    "password": "TempPass123!",
    "firstName": "Alice",
    "lastName": "Johnson",
    "userType": "REGULAR"
  }'
```

### PUT /api/v1/admin/users/{id}

Update an existing user account (admin only).

**Replaces**: COUSR02.bms (User Update Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | User ID to update |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body**:
```json
{
  "firstName": "Alice",
  "lastName": "Johnson-Smith",
  "userType": "ADMIN",
  "status": "ACTIVE"
}
```

**Request Schema**:
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| firstName | string | No | Max: 25 characters | First name |
| lastName | string | No | Max: 25 characters | Last name |
| userType | string | No | Enum: REGULAR, ADMIN | User type |
| status | string | No | Enum: ACTIVE, INACTIVE, LOCKED | User status |
| password | string | No | 8 characters minimum | New password (optional) |

**Success Response** (200 OK):
```json
{
  "userId": 3,
  "username": "USER0003",
  "firstName": "Alice",
  "lastName": "Johnson-Smith",
  "userType": "ADMIN",
  "status": "ACTIVE",
  "updatedAt": "2024-01-15T11:30:00Z"
}
```

**Error Responses**:

400 Bad Request - Validation error:
```json
{
  "timestamp": "2024-01-15T11:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: Invalid user status",
  "path": "/api/v1/admin/users/3"
}
```

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/users/3"
}
```

404 Not Found - User doesn't exist:
```json
{
  "timestamp": "2024-01-15T11:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User with ID 3 not found",
  "path": "/api/v1/admin/users/3"
}
```

**Usage Example**:
```bash
curl -X PUT http://localhost:8080/api/v1/admin/users/3 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "userType": "ADMIN",
    "status": "ACTIVE"
  }'
```

### DELETE /api/v1/admin/users/{id}

Delete a user account (admin only).

**Replaces**: COUSR03.bms (User Delete Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | User ID to delete |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (204 No Content):
```
(Empty response body)
```

**Error Responses**:

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:35:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/users/3"
}
```

404 Not Found - User doesn't exist:
```json
{
  "timestamp": "2024-01-15T11:35:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User with ID 3 not found",
  "path": "/api/v1/admin/users/3"
}
```

409 Conflict - Cannot delete self:
```json
{
  "timestamp": "2024-01-15T11:35:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Cannot delete currently logged in user",
  "path": "/api/v1/admin/users/1"
}
```

**Usage Example**:
```bash
curl -X DELETE http://localhost:8080/api/v1/admin/users/3 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Menu Endpoints

### GET /api/v1/menu

Retrieve main application menu options.

**Replaces**: COMEN01.bms (Main Menu Screen)

**Security**: Requires JWT token with ROLE_USER or ROLE_ADMIN

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "title": "CardDemo Main Menu",
  "menuItems": [
    {
      "optionId": "1",
      "label": "Account",
      "description": "View and update account information",
      "action": "/api/v1/accounts"
    },
    {
      "optionId": "2",
      "label": "Card",
      "description": "View and manage credit cards",
      "action": "/api/v1/cards"
    },
    {
      "optionId": "3",
      "label": "Bill Payment",
      "description": "Make a payment on your account",
      "action": "/api/v1/accounts/{accountId}/payments"
    },
    {
      "optionId": "4",
      "label": "Transactions",
      "description": "View transaction history",
      "action": "/api/v1/transactions"
    },
    {
      "optionId": "5",
      "label": "Administration",
      "description": "User administration (admin only)",
      "action": "/api/v1/admin",
      "requiredRole": "ROLE_ADMIN"
    }
  ]
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| title | string | Menu title |
| menuItems | array | List of menu options |
| menuItems[].optionId | string | Option identifier |
| menuItems[].label | string | Menu option label |
| menuItems[].description | string | Option description |
| menuItems[].action | string | Associated API endpoint or action |
| menuItems[].requiredRole | string | Required role (optional) |

**Usage Example**:
```bash
curl -X GET http://localhost:8080/api/v1/menu \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### GET /api/v1/admin/menu

Retrieve administrative menu options.

**Replaces**: COADM01.bms (Admin Menu Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK):
```json
{
  "title": "CardDemo Administration Menu",
  "menuItems": [
    {
      "optionId": "1",
      "label": "User Management",
      "description": "Add, update, or delete users",
      "action": "/api/v1/admin/users"
    },
    {
      "optionId": "2",
      "label": "Reports",
      "description": "Generate system reports",
      "action": "/api/v1/reports"
    },
    {
      "optionId": "3",
      "label": "System Configuration",
      "description": "View and update system settings",
      "action": "/api/v1/admin/config"
    }
  ]
}
```

**Error Responses**:

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:40:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/menu"
}
```

**Usage Example**:
```bash
curl -X GET http://localhost:8080/api/v1/admin/menu \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Report Endpoints

### GET /api/v1/reports

Generate or retrieve system reports.

**Replaces**: CORPT00.bms (Report Screen)

**Security**: Requires JWT token with ROLE_ADMIN

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| reportType | string | Yes | - | Report type (TRANSACTIONS, ACCOUNTS, USERS, ACTIVITY) |
| startDate | string (date) | No | - | Start date (YYYY-MM-DD) |
| endDate | string (date) | No | - | End date (YYYY-MM-DD) |
| format | string | No | JSON | Output format (JSON, CSV, PDF) |

**Request Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response** (200 OK) - Transaction Report:
```json
{
  "reportType": "TRANSACTIONS",
  "reportTitle": "Transaction Activity Report",
  "generatedAt": "2024-01-15T11:45:00Z",
  "parameters": {
    "startDate": "2024-01-01",
    "endDate": "2024-01-15"
  },
  "summary": {
    "totalTransactions": 1250,
    "totalAmount": 156789.45,
    "purchaseCount": 1000,
    "purchaseAmount": 145000.00,
    "paymentCount": 200,
    "paymentAmount": 35000.00,
    "refundCount": 50,
    "refundAmount": 5210.55
  },
  "data": [
    {
      "date": "2024-01-15",
      "transactionCount": 85,
      "transactionAmount": 10234.56
    },
    {
      "date": "2024-01-14",
      "transactionCount": 92,
      "transactionAmount": 11456.78
    }
  ]
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| reportType | string | Type of report |
| reportTitle | string | Report title |
| generatedAt | string (ISO 8601) | Report generation timestamp |
| parameters | object | Report parameters used |
| summary | object | Summary statistics |
| data | array | Report data rows |

**Error Responses**:

400 Bad Request - Invalid report type:
```json
{
  "timestamp": "2024-01-15T11:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid report type. Must be one of: TRANSACTIONS, ACCOUNTS, USERS, ACTIVITY",
  "path": "/api/v1/reports"
}
```

403 Forbidden - Insufficient permissions:
```json
{
  "timestamp": "2024-01-15T11:45:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/reports"
}
```

**Usage Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/reports?reportType=TRANSACTIONS&startDate=2024-01-01&endDate=2024-01-15&format=JSON" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Common Schemas

### LoginRequest Schema
```json
{
  "username": "string (8 characters)",
  "password": "string (8 characters)"
}
```

### LoginResponse Schema
```json
{
  "accessToken": "string (JWT)",
  "tokenType": "string",
  "expiresIn": "integer (seconds)",
  "issuedAt": "string (ISO 8601)",
  "user": {
    "userId": "integer",
    "username": "string",
    "firstName": "string",
    "lastName": "string",
    "userType": "string",
    "roles": ["string"]
  }
}
```

### AccountResponse Schema
```json
{
  "accountId": "integer",
  "accountNumber": "string (11 digits)",
  "accountStatus": "string",
  "currentBalance": "number",
  "availableCredit": "number",
  "creditLimit": "number",
  "cashAdvanceLimit": "number",
  "openDate": "string (date)",
  "expirationDate": "string (date)",
  "customer": {
    "customerId": "integer",
    "firstName": "string",
    "lastName": "string",
    "addressLine1": "string",
    "city": "string",
    "state": "string",
    "zipCode": "string"
  }
}
```

### CardResponse Schema
```json
{
  "cardId": "integer",
  "cardNumber": "string (16 digits)",
  "accountNumber": "string (11 digits)",
  "cardStatus": "string",
  "cardType": "string",
  "expirationDate": "string (date)",
  "cardholderName": "string"
}
```

### TransactionResponse Schema
```json
{
  "transactionId": "string (16 characters)",
  "accountId": "integer",
  "cardNumber": "string (16 digits)",
  "transactionDate": "string (date)",
  "transactionType": "string",
  "transactionCategory": "string",
  "description": "string (26 characters)",
  "amount": "number",
  "merchantName": "string",
  "merchantCity": "string",
  "merchantState": "string"
}
```

### PaginationMetadata Schema
```json
{
  "page": "integer",
  "size": "integer",
  "totalElements": "integer",
  "totalPages": "integer",
  "first": "boolean",
  "last": "boolean"
}
```

### ErrorResponse Schema
```json
{
  "timestamp": "string (ISO 8601)",
  "status": "integer (HTTP status code)",
  "error": "string (HTTP status text)",
  "message": "string (detailed error message)",
  "path": "string (request path)"
}
```

---

## HTTP Status Codes

The CardDemo API uses standard HTTP status codes to indicate the success or failure of requests:

### Success Codes

| Code | Status | Usage |
|------|--------|-------|
| 200 | OK | Successful GET or PUT request |
| 201 | Created | Successful POST request creating a new resource |
| 204 | No Content | Successful DELETE request or logout |

### Client Error Codes

| Code | Status | Usage |
|------|--------|-------|
| 400 | Bad Request | Malformed request, validation failure, invalid input |
| 401 | Unauthorized | Missing, invalid, or expired JWT token |
| 403 | Forbidden | Valid token but insufficient permissions for the operation |
| 404 | Not Found | Requested resource does not exist |
| 409 | Conflict | Request conflicts with current state (duplicate resource, business rule violation) |
| 422 | Unprocessable Entity | Request is well-formed but semantically incorrect (e.g., insufficient funds) |

### Server Error Codes

| Code | Status | Usage |
|------|--------|-------|
| 500 | Internal Server Error | Unexpected server error, application crash |
| 503 | Service Unavailable | Service temporarily unavailable (maintenance, overload) |

---

## Error Handling

All error responses follow a consistent format:

### Error Response Structure
```json
{
  "timestamp": "2024-01-15T11:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message describing what went wrong",
  "path": "/api/v1/accounts/1234567890"
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| timestamp | string (ISO 8601) | Timestamp when the error occurred |
| status | integer | HTTP status code (400, 401, 403, 404, etc.) |
| error | string | HTTP status text (Bad Request, Unauthorized, etc.) |
| message | string | Human-readable error description |
| path | string | API endpoint path where error occurred |

### Validation Error Response

For requests with multiple validation errors:

```json
{
  "timestamp": "2024-01-15T11:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/accounts/1234567890",
  "errors": [
    {
      "field": "creditLimit",
      "rejectedValue": -100,
      "message": "creditLimit must be greater than or equal to 0"
    },
    {
      "field": "customer.zipCode",
      "rejectedValue": "ABC",
      "message": "zipCode must contain only digits"
    }
  ]
}
```

### Authentication Error Response

Missing or invalid JWT token:

```json
{
  "timestamp": "2024-01-15T11:50:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/v1/accounts/1234567890"
}
```

Expired JWT token:

```json
{
  "timestamp": "2024-01-15T11:50:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token has expired. Please login again",
  "path": "/api/v1/accounts/1234567890"
}
```

### Authorization Error Response

Valid token but insufficient permissions:

```json
{
  "timestamp": "2024-01-15T11:50:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/users"
}
```

---

## Pagination

List endpoints that return multiple items support pagination to limit response size and improve performance.

### Pagination Parameters

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| page | integer | 0 | - | Page number (zero-indexed) |
| size | integer | 10 | 100 | Number of items per page |
| sort | string | varies | - | Sort criteria (field,direction) |

### Pagination Metadata

All paginated responses include a `pagination` object:

```json
{
  "data": [...],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 125,
    "totalPages": 13,
    "first": true,
    "last": false
  }
}
```

### Pagination Metadata Fields

| Field | Type | Description |
|-------|------|-------------|
| page | integer | Current page number (zero-indexed) |
| size | integer | Number of items per page |
| totalElements | integer | Total number of items across all pages |
| totalPages | integer | Total number of pages |
| first | boolean | True if this is the first page |
| last | boolean | True if this is the last page |

### Sort Parameter Format

The `sort` parameter accepts comma-separated field and direction:

```
?sort=fieldName,direction
```

Examples:
- `?sort=transactionDate,desc` - Sort by transaction date descending
- `?sort=username,asc` - Sort by username ascending
- `?sort=amount,desc` - Sort by amount descending

### Pagination Examples

**First page (default)**:
```
GET /api/v1/accounts/1234567890/transactions?page=0&size=10
```

**Second page**:
```
GET /api/v1/accounts/1234567890/transactions?page=1&size=10
```

**Large page size**:
```
GET /api/v1/accounts/1234567890/transactions?page=0&size=50
```

**With sorting**:
```
GET /api/v1/accounts/1234567890/transactions?page=0&size=10&sort=transactionDate,desc
```

---

## BMS Screen to REST API Mapping

This section documents the complete mapping from legacy BMS 3270 screens to modern RESTful API endpoints, demonstrating functional equivalence between the COBOL/CICS application and the Java 21 modernized application.

### Comprehensive Mapping Table

| BMS Screen | Screen Name | Primary Function | REST Endpoint(s) | HTTP Method(s) | COBOL Program | Notes |
|------------|-------------|------------------|------------------|----------------|---------------|-------|
| COSGN00.bms | Login Screen | User authentication | `/api/v1/auth/login` | POST | COSGN00C.cbl | Returns JWT token instead of CICS session |
| | | Session termination | `/api/v1/auth/logout` | POST | COSGN00C.cbl | Invalidates JWT token |
| COMEN01.bms | Main Menu | Display menu options | `/api/v1/menu` | GET | COMEN01C.cbl | Returns JSON menu structure |
| COADM01.bms | Admin Menu | Display admin options | `/api/v1/admin/menu` | GET | COADM01C.cbl | Requires ROLE_ADMIN |
| COACTVW.bms | Account View | View account details | `/api/v1/accounts/{id}` | GET | COACTVWC.cbl | Returns complete account + customer data |
| COACTUP.bms | Account Update | Update account info | `/api/v1/accounts/{id}` | PUT | COACTUPC.cbl | Accepts partial updates |
| COCRDLI.bms | Card List | List cards for account | `/api/v1/accounts/{accountId}/cards` | GET | COCRDLIC.cbl | Supports pagination and filtering |
| COCRDSL.bms | Card Select | View single card | `/api/v1/cards/{cardNumber}` | GET | COCRDSLC.cbl | Returns detailed card information |
| COCRDUP.bms | Card Update | Update card info | `/api/v1/cards/{id}` | PUT | COCRDUPC.cbl | Requires ROLE_ADMIN |
| COTRN00.bms | Transaction List | List transactions | `/api/v1/accounts/{accountId}/transactions` | GET | COTRN00C.cbl | Pagination, filtering, sorting |
| COTRN01.bms | Transaction View | View transaction detail | `/api/v1/transactions/{id}` | GET | COTRN01C.cbl | Full transaction details |
| COTRN02.bms | Transaction Add | Add manual transaction | `/api/v1/transactions` | POST | COTRN02C.cbl | Requires ROLE_ADMIN |
| COBIL00.bms | Bill Payment | Process payment | `/api/v1/accounts/{accountId}/payments` | POST | COBIL00C.cbl | Returns confirmation number |
| COUSR00.bms | User List | List all users | `/api/v1/admin/users` | GET | COUSR00C.cbl | Requires ROLE_ADMIN |
| COUSR01.bms | User Add | Create new user | `/api/v1/admin/users` | POST | COUSR01C.cbl | Requires ROLE_ADMIN |
| COUSR02.bms | User Update | Update user info | `/api/v1/admin/users/{id}` | PUT | COUSR02C.cbl | Requires ROLE_ADMIN |
| COUSR03.bms | User Delete | Delete user | `/api/v1/admin/users/{id}` | DELETE | COUSR03C.cbl | Requires ROLE_ADMIN |
| CORPT00.bms | Report Screen | Generate reports | `/api/v1/reports` | GET | CORPT00C.cbl | Query parameters for report type and dates |

### Field Mapping Examples

#### Login Screen (COSGN00.bms → POST /api/v1/auth/login)

| BMS Field | Type | Max Length | JSON Field | Type | Validation |
|-----------|------|------------|------------|------|------------|
| USERID | Input | 8 | username | string | Required, 8 chars |
| PASSWD | Input (hidden) | 8 | password | string | Required, 8 chars |
| ERRMSG | Output | 78 | message | string | Error message in 401 response |

#### Account View Screen (COACTVW.bms → GET /api/v1/accounts/{id})

| BMS Field | Type | Max Length | JSON Field | Type | Notes |
|-----------|------|------------|------------|------|-------|
| ACCTSID | Input | 11 | id (path param) | integer | Account ID |
| ACCTNO | Output | 11 | accountNumber | string | 11-digit account number |
| ACSTATUS | Output | 10 | accountStatus | string | ACTIVE, CLOSED, SUSPENDED |
| ACURBAL | Output | 13 | currentBalance | number | Current balance |
| ACRDLIM | Output | 13 | creditLimit | number | Credit limit |
| ACSHLIM | Output | 13 | cashAdvanceLimit | number | Cash advance limit |
| AOPNDATE | Output | 10 | openDate | string (date) | Account open date |
| AEXPDATE | Output | 10 | expirationDate | string (date) | Expiration date |
| ACFNAME | Output | 25 | customer.firstName | string | Customer first name |
| ACLNAME | Output | 25 | customer.lastName | string | Customer last name |
| ACADL1 | Output | 50 | customer.addressLine1 | string | Address line 1 |
| ACCITY | Output | 50 | customer.city | string | City |
| ACSTATE | Output | 2 | customer.state | string | State code |
| ACPOSTAL | Output | 10 | customer.zipCode | string | ZIP code |

#### Transaction List Screen (COTRN00.bms → GET /api/v1/accounts/{accountId}/transactions)

| BMS Field | Type | Max Length | JSON Field | Type | Notes |
|-----------|------|------------|------------|------|-------|
| ACCTSID | Input | 11 | accountId (path param) | integer | Account ID |
| CARDSID | Input | 16 | cardNumber (query param) | string | Optional filter |
| PAGENO | Output | 5 | pagination.page | integer | Current page number |
| SEL0001-10 | Input | 1 | - | - | Selection handled by GET /transactions/{id} |
| TRNID01-10 | Output | 16 | transactions[].transactionId | string | Transaction ID |
| TDATE01-10 | Output | 8 | transactions[].transactionDate | string | Transaction date |
| TDESC01-10 | Output | 26 | transactions[].description | string | Description |
| TAMT001-10 | Output | 12 | transactions[].amount | number | Amount |

### Authentication Flow Comparison

**Legacy COBOL/CICS Flow**:
1. User enters credentials on COSGN00 screen
2. COSGN00C.cbl validates against USRSEC file
3. CICS creates session with commarea (COCOM01Y)
4. User navigates via XCTL commands

**Modern REST API Flow**:
1. Client POSTs credentials to `/api/v1/auth/login`
2. AuthenticationService validates against User table
3. JwtTokenProvider generates JWT token (1-hour expiration)
4. Client includes token in Authorization header for subsequent requests
5. JwtAuthenticationFilter validates token on each request

### Data Retrieval Comparison

**Legacy COBOL/CICS**:
```cobol
EXEC CICS READ
    FILE('ACCTFILE')
    RIDFLD(WS-ACCT-ID)
    INTO(ACCOUNT-RECORD)
    LENGTH(LENGTH OF ACCOUNT-RECORD)
END-EXEC.
EXEC CICS SEND MAP('COACTVWA')
    MAPSET('COACTVW')
    FROM(ACCOUNT-RECORD)
    ERASE
END-EXEC.
```

**Modern REST API**:
```http
GET /api/v1/accounts/1234567890 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Accept: application/json
```

Response:
```json
{
  "accountId": 1234567890,
  "accountNumber": "00000000001",
  "currentBalance": 1234.56,
  ...
}
```

### Update Operation Comparison

**Legacy COBOL/CICS**:
```cobol
EXEC CICS RECEIVE MAP('CACTUPA')
    MAPSET('COACTUP')
    INTO(ACCOUNT-UPDATE-AREA)
END-EXEC.
* Validate input fields
EXEC CICS READ UPDATE
    FILE('ACCTFILE')
    RIDFLD(WS-ACCT-ID)
    INTO(ACCOUNT-RECORD)
END-EXEC.
* Update fields
EXEC CICS REWRITE
    FILE('ACCTFILE')
    FROM(ACCOUNT-RECORD)
END-EXEC.
EXEC CICS SYNCPOINT.
```

**Modern REST API**:
```http
PUT /api/v1/accounts/1234567890 HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "creditLimit": 15000.00,
  "cashAdvanceLimit": 7500.00
}
```

Response:
```json
{
  "accountId": 1234567890,
  "creditLimit": 15000.00,
  "cashAdvanceLimit": 7500.00,
  "updatedAt": "2024-01-15T12:00:00Z"
}
```

---

## Additional Resources

### Swagger UI Documentation

Interactive API documentation is available via Swagger UI when the application is running:

**URL**: `http://localhost:8080/swagger-ui.html`

Features:
- Browse all endpoints
- View request/response schemas
- Test endpoints directly from the browser
- Download OpenAPI 3.0 specification (JSON/YAML)

### Postman Collection

A comprehensive Postman collection is available for testing all API endpoints:

**Location**: `/docs/postman/CardDemo-API-Collection.json`

To import:
1. Open Postman
2. Click Import
3. Select the collection file
4. Update environment variables (baseUrl, JWT token)

### Example Client Implementations

Sample client implementations demonstrating API usage:

- **JavaScript**: `/docs/examples/javascript-client.js`
- **Python**: `/docs/examples/python-client.py`
- **Java**: `/docs/examples/JavaClient.java`
- **curl**: `/docs/examples/curl-examples.sh`

### Related Documentation

- [Modernization Guide](./modernization.md) - COBOL to Java migration details
- [Architecture Documentation](./architecture.md) - System architecture overview
- [Data Migration Guide](./data-migration.md) - VSAM to PostgreSQL migration
- [Deployment Guide](./deployment-guide.md) - Docker and Kubernetes deployment

---

**Document Version**: 1.0.0  
**Last Updated**: 2024-01-15  
**Maintained By**: CardDemo Development Team  
**Questions or Issues**: Contact support@carddemo.example.com
