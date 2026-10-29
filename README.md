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
- Answer general financial questions through a local Ollama model using Spring AI.
- Summarize stored transactions with status-aware totals and optional date filtering.

### Planned

- AI-assisted transaction analysis using validated backend operations.
- Validated backend operations for any future AI tool calls.
- Authentication and frontend functionality.

The `/api/ask` endpoint currently handles general financial questions only. It is not connected to transaction records. Transaction summary statistics are calculated by the backend endpoint; no AI tool calling, RAG, authentication, payment integration, or frontend features are implemented.

## Technology stack

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring AI ChatClient with Ollama
- Spring Data JPA / Hibernate
- MySQL
- Jakarta Bean Validation
- JUnit 5, Mockito, and Spring MVC test support

## Prerequisites

- JDK 21
- MySQL running locally
- Ollama running locally with the `codellama:latest` model available
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

## Ollama setup

Ollama must be installed and running separately from the Spring Boot application. Confirm the local server and installed model in PowerShell:

```powershell
ollama --version
ollama list
```

Ensure `codellama:latest` appears in the model list. Start the Ollama service if it is not already running:

```powershell
ollama serve
```

The application defaults to `http://localhost:11434` and `codellama:latest`. Override these values with `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, and optionally `OLLAMA_TEMPERATURE` before starting Spring Boot:

```powershell
$env:OLLAMA_BASE_URL = "http://localhost:11434"
$env:OLLAMA_MODEL = "codellama:latest"
$env:OLLAMA_TEMPERATURE = "0.7"
```

The request path is `AskController` → `AskService`/`AskServiceImpl` → Spring AI `ChatClient` → local Ollama. The service passes the submitted question with a system instruction that prevents claiming access to personal accounts or inventing transaction information. Ollama errors produce a service-unavailable response rather than a successful fabricated answer.

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
| POST | `/api/ask` | `200 OK`, generated answer; `400` for invalid question; `503` if AI service is unavailable |

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

### Transaction summary

`GET /api/transactions/summary` returns transaction statistics from the stored data. It accepts optional `startDate` and `endDate` parameters in the same ISO local date-time format used by transaction search. Dates are inclusive; either bound may be supplied alone. `startDate` after `endDate` returns `400 Bad Request`.

The response is a dedicated summary DTO:

```json
{
  "totalIncome": 3200.00,
  "totalExpenses": 245.50,
  "netBalance": 2954.50,
  "totalTransactions": 5,
  "completedTransactions": 2,
  "pendingTransactions": 2,
  "failedTransactions": 1,
  "expenseTotalsByCategory": [
    { "category": "FOOD", "totalAmount": 145.50 },
    { "category": "TRAVEL", "totalAmount": 100.00 }
  ],
  "incomeTotalsByCategory": [
    { "category": "SALARY", "totalAmount": 3200.00 }
  ]
}
```

Only `COMPLETED` transactions contribute to income, expenses, net balance, or category totals. Transaction counts include all transactions in the selected date range and separately report `COMPLETED`, `PENDING`, and `FAILED`. For example, the response above has 3,200.00 completed income minus 245.50 completed expenses, yielding a 2,954.50 net balance. Pending and failed transactions are counted but do not change those financial totals. Empty date ranges return zero totals and empty category arrays.

Example requests:

```text
GET http://localhost:8080/api/transactions/summary
GET http://localhost:8080/api/transactions/summary?startDate=2026-10-01T00:00:00&endDate=2026-10-27T23:59:59
GET http://localhost:8080/api/transactions/summary?startDate=2026-10-01T00:00:00
GET http://localhost:8080/api/transactions/summary?startDate=2026-10-28T00:00:00&endDate=2026-10-27T23:59:59
```

The last request returns `400 Bad Request`. Malformed date values are also rejected by the existing query-parameter error handling.

The repository uses database-side `SUM`, `COUNT`, and `GROUP BY` aggregates. The database processes matching rows and returns compact totals rather than loading every transaction into Java, which reduces application memory use and transfer volume as the table grows. The service safely converts SQL `NULL` aggregate values to zero, computes net balance with `BigDecimal`, and splits grouped results into expense and income category lists. It uses a straightforward loop for this small transformation; Java Streams are not used to regroup transaction rows because grouping is already done efficiently by the database.

The summary route is a separate literal mapping under `/api/transactions`; it does not replace the collection list or the numeric `/{id}` resource route.

### Ask a general financial question

`POST /api/ask` accepts a nonblank question and returns the original question with an answer generated by the configured local Ollama model. Send `Content-Type: application/json`.

```json
{
  "question": "Explain the difference between saving and investing."
}
```

Example response (`200 OK`; the generated wording may vary):

```json
{
  "question": "Explain the difference between saving and investing.",
  "answer": "Saving generally keeps money accessible for near-term needs, while investing puts money into assets for potential long-term growth and carries a risk of loss."
}
```

Blank, whitespace-only, or missing questions return `400 Bad Request` with a field-specific `details.question` message. If Ollama cannot generate a nonblank answer, the API returns `503 Service Unavailable` with a generic error body. This endpoint does not read MySQL transactions: a question such as “How much did I spend this month?” must not be answered with an invented amount and the assistant is instructed to explain that transaction data is unavailable to it.

Try these questions:

```json
{"question":"What is compound interest?"}
{"question":"How can I create a monthly budget?"}
{"question":"How much did I spend this month?"}
```

## AI limitations

The local model provides general responses and can be inaccurate; verify important financial decisions independently. The AI endpoint does not access transaction rows, account balances, or bank accounts. There is no conversation memory, arbitrary SQL generation, AI tool calling, embeddings, or RAG.

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

Import [`FinAssist/postman/FinAssist-AI.postman_collection.json`](FinAssist/postman/FinAssist-AI.postman_collection.json) into Postman. It contains AI ask and transaction CRUD, filters, and summary requests. Set the collection variable `baseUrl` to `http://localhost:8080` and `transactionId` to an ID returned by the create request.

## Tests

Run from the repository root:

```powershell
Set-Location .\FinAssist
.\mvnw.cmd test
```

The tests cover the MVC request/response behavior, validation, invalid enum filters, not-found responses, CRUD service operations, filtering inputs, invalid amount/pagination ranges, summary metrics and category totals, empty summaries, date-range behavior, summary route mapping, AI question validation, prompt forwarding, and model failure handling. They mock AI/database dependencies and do not require MySQL or Ollama; the running application requires MySQL, and `/api/ask` requires Ollama.
