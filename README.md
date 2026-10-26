# FinAssist-AI

FinAssist AI is a financial transaction analysis assistant. This repository is being built in milestones: the first milestone provides a transaction-management API as a safe foundation for future analysis features.

## Current implementation status

### Implemented

- Create, list, retrieve, update, and delete transactions over REST.
- Persist transactions with Spring Data JPA and MySQL.
- Validate transaction input and report client errors in a consistent JSON format.
- Filter and paginate transaction lists.
- Use `BigDecimal` for money and string-backed enums for transaction type and status.
- Create/update the database schema without dropping existing data.

### Planned

- Natural-language analysis and summaries using Spring AI and Ollama.
- Validated backend operations for any future AI tool calls.
- Authentication and frontend functionality.

No AI summaries, tool calling, RAG, authentication, payment integration, or frontend features are implemented in this milestone. The Ollama starter already present in the original Maven configuration is not used by the transaction API.

## Technology stack

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA / Hibernate
- MySQL
- Jakarta Bean Validation
- JUnit 5, Mockito, and Spring MVC test support

## Prerequisites

- JDK 21
- MySQL running locally
- A shell in the repository root (the `FinAssist` directory contains the Maven project and wrapper)

## Local database and configuration

Create the development database once in MySQL:

```sql
CREATE DATABASE finassistdb;
```

The application defaults to `jdbc:mysql://localhost:3306/finassistdb` and username `root`. Configure the local password through environment variables; no database password is committed:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-local-password"
```

Optional overrides:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/finassistdb"
```

The JPA setting `spring.jpa.hibernate.ddl-auto=update` lets Hibernate create or update the `transactions` table without dropping existing tables or rows. The application must be able to connect to MySQL at startup. The table is not created if the database is unavailable.

## Run the application

From the repository root in PowerShell:

```powershell
Set-Location .\FinAssist
.\mvnw.cmd spring-boot:run
```

The API listens at `http://localhost:8080`. You can confirm the table was created by connecting to `finassistdb` and running:

```sql
SHOW TABLES;
DESCRIBE transactions;
```

The `transactions` table stores transaction type and status as readable enum strings. Amounts use `DECIMAL(19,2)`. The `createdAt` timestamp is assigned when a transaction is first persisted and cannot be updated through the API.

## REST API

All request and response bodies use JSON. Send `Content-Type: application/json` for POST and PUT. Timestamps use ISO local date-time format, for example `2026-10-24T12:30:00`.

| Method | URL | Successful response |
| --- | --- | --- |
| POST | `/api/transactions` | `201 Created`, response body and `Location` header |
| GET | `/api/transactions` | `200 OK`, paginated response |
| GET | `/api/transactions/{id}` | `200 OK`, one transaction; `404 Not Found` if absent |
| PUT | `/api/transactions/{id}` | `200 OK`, updated transaction; `404 Not Found` if absent |
| DELETE | `/api/transactions/{id}` | `204 No Content`; `404 Not Found` if absent |

### Create a transaction

`POST /api/transactions` — returns `201 Created`, a `Location` header, and the created transaction. Status defaults to `PENDING` when omitted.

```json
{
  "transactionType": "EXPENSE",
  "amount": 42.75,
  "category": "FOOD",
  "merchant": "Harbor Cafe",
  "description": "Lunch",
  "transactionDate": "2026-10-24T12:30:00"
}
```

Example response (`201 Created`):

```json
{
  "id": 12,
  "transactionType": "EXPENSE",
  "amount": 42.75,
  "category": "FOOD",
  "merchant": "Harbor Cafe",
  "description": "Lunch",
  "transactionDate": "2026-10-24T12:30:00",
  "status": "PENDING",
  "createdAt": "2026-10-24T12:31:00"
}
```

Valid transaction types are `INCOME` and `EXPENSE`; valid statuses are `PENDING`, `COMPLETED`, and `FAILED`. The amount must be positive with no more than two decimal places. Category and merchant are required.

### List and filter transactions

`GET /api/transactions` — returns a page of transactions, newest transaction date first. Defaults are `page=0` and `size=20`; page must be non-negative and size must be 1–100. The response contains `content`, `page`, `size`, `totalElements`, `totalPages`, and `last`.

Supported optional filters can be combined:

| Query parameter | Meaning |
| --- | --- |
| `minAmount`, `maxAmount` | Inclusive amount bounds |
| `status` | `PENDING`, `COMPLETED`, or `FAILED` |
| `transactionType` | `INCOME` or `EXPENSE` |
| `category` | Exact category match, such as `FOOD` |
| `startDate`, `endDate` | Inclusive ISO local date-time bounds |
| `page`, `size` | Pagination |

Examples:

```text
GET http://localhost:8080/api/transactions
GET http://localhost:8080/api/transactions?minAmount=100&maxAmount=5000
GET http://localhost:8080/api/transactions?status=COMPLETED
GET http://localhost:8080/api/transactions?transactionType=EXPENSE&category=FOOD
GET http://localhost:8080/api/transactions?startDate=2026-10-01T00:00:00&endDate=2026-10-25T23:59:59
GET http://localhost:8080/api/transactions?minAmount=100&maxAmount=5000&status=COMPLETED&transactionType=EXPENSE&category=FOOD
```

Example list response (`200 OK`):

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "last": true
}
```

When provided, `minAmount` must be greater than zero and cannot exceed `maxAmount`. `startDate` cannot be after `endDate`. Invalid enum values, date formats, and ranges return `400 Bad Request` with the invalid parameter identified in `details`.

### Retrieve one transaction

`GET /api/transactions/{id}` — returns `200 OK` and the transaction, or `404 Not Found` if it does not exist.

### Update a transaction

`PUT /api/transactions/{id}` — replaces all editable transaction fields and returns `200 OK`. All fields except `description` are required, including `status`.

```json
{
  "transactionType": "EXPENSE",
  "amount": 48.25,
  "category": "FOOD",
  "merchant": "Harbor Cafe",
  "description": "Updated lunch total",
  "transactionDate": "2026-10-24T12:30:00",
  "status": "COMPLETED"
}
```

### Delete a transaction

`DELETE /api/transactions/{id}` — returns `204 No Content`, or `404 Not Found` if no transaction exists.

### Error response

Validation and filter errors return `400`; missing transaction IDs return `404`. Responses use this shape without exposing internal exception details:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "details": {
    "amount": "must be greater than or equal to 0.01"
  }
}
```

Malformed query values identify the query parameter and explain the expected format or enum values. Unexpected server errors return `500` with a generic message; details are logged server-side and are not returned to the client.

Validation examples:

```text
POST http://localhost:8080/api/transactions
Content-Type: application/json

{"transactionType":"EXPENSE","amount":42.75,"category":"FOOD","merchant":"Harbor Cafe","transactionDate":"2026-10-24T12:30:00"}
```

Expected: `201 Created`.

```text
POST http://localhost:8080/api/transactions
Content-Type: application/json

{"transactionType":"EXPENSE","amount":0,"category":"FOOD","merchant":"Harbor Cafe","transactionDate":"2026-10-24T12:30:00"}
```

Expected: `400 Bad Request`, with an amount validation message under `details.amount`.

```text
GET http://localhost:8080/api/transactions?minAmount=0
GET http://localhost:8080/api/transactions?status=UNKNOWN
GET http://localhost:8080/api/transactions?startDate=not-a-date
```

Expected: `400 Bad Request`, with `details.minAmount`, `details.status`, or `details.startDate` identifying the invalid parameter.

## Postman

Import [`FinAssist/postman/FinAssist-AI.postman_collection.json`](FinAssist/postman/FinAssist-AI.postman_collection.json) into Postman. It contains create, list, amount/status/type/category/date filters, get-by-ID, update, and delete requests. Set the collection variable `baseUrl` to `http://localhost:8080` and `transactionId` to an ID returned by the create request.

## Tests

Run from the repository root:

```powershell
Set-Location .\FinAssist
.\mvnw.cmd test
```

The tests cover the MVC request/response behavior, validation, invalid enum filters, not-found responses, CRUD service operations, filtering inputs, and invalid amount/pagination ranges. They use mocks and do not require a running MySQL server; the application itself does require MySQL.
