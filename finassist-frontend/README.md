# FinAssist AI frontend

Angular 19 standalone frontend for the existing FinAssist Spring Boot API. It displays real backend data and does not include fixture/demo financial values in the application.

## Run locally

Requirements: Node.js 20.11.1+ on the Node 20 line, npm, the Spring Boot backend on port `8080`, and a configured MySQL database. The AI assistant additionally needs local Ollama and the configured model.

From this directory:

```powershell
npm install
npm start
```

Open [http://localhost:4200](http://localhost:4200). The development proxy in `proxy.conf.json` forwards `/api` to `http://localhost:8080`. The API base path is centralized in `src/environments/environment.ts`. No credentials or database configuration are included in the frontend.

## Features

- Dashboard backed by `GET /api/transactions/summary` and `GET /api/transactions`.
- Transaction list with backend pagination and filters supported by the API.
- Create, full-update, and delete calls to `/api/transactions`.
- AI questions posted to `POST /api/ask`; structured backend evidence is displayed when returned.
- Responsive navigation, input validation, loading, empty, retry, and error states.

Financial amounts are shown without a currency marker because the API does not specify a currency. The backend is unauthenticated and currently exposes a shared demo dataset; use synthetic records only. Chat is held in component memory and is not stored persistently.

## Checks

```powershell
npm run build
npm test -- --watch=false --browsers=ChromeHeadless
```
