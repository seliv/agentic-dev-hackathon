# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

### Full stack (Docker Compose)
```
docker-compose up          # PostgreSQL :5432, backend :8080, frontend :5173
docker-compose up --build  # rebuild after code changes
```

### Backend (Spring Boot, Java 21)
```
cd backend
./gradlew build       # compile + test
./gradlew bootRun     # run locally on :8080 (needs PostgreSQL on :5432)
```

### Frontend (React 19 + Vite + TypeScript)
```
cd frontend
npm install
npm run dev           # dev server on :5173
npm run build
npm run lint
```

## Architecture

**Backend** (`backend/src/main/java/com/chatapp/`): Spring Boot 3.5.6 with Spring Data JPA, Spring Security, Liquibase migrations. Session-based authentication using HttpSession + BCrypt. CORS allows `localhost:*` with credentials.

**Frontend** (`frontend/src/`): React 19, React Router v7, Ant Design 5. Axios client with 401 interceptor for auto-redirect. AuthContext manages auth state; ProtectedRoute guards pages. API base URL from `VITE_API_URL` (default `http://localhost:8080/api`).

**Database**: PostgreSQL 16. Schema managed by Liquibase (`backend/src/main/resources/db/changelog/`). Docker Compose credentials: user/password/db all `chatapp`.

## Key Paths

- `backend/src/main/java/com/chatapp/controller/` — REST endpoints (AuthController, UserController)
- `backend/src/main/java/com/chatapp/security/` — SessionAuthenticationFilter
- `backend/src/main/resources/application.yml` — Spring config
- `frontend/src/api/` — Axios client and API functions
- `frontend/src/context/AuthContext.tsx` — auth state management
- `frontend/src/pages/` — SignUp, SignIn, Home
