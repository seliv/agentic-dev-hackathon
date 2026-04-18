# Backend

## Coding Guidelines

**For AI Assistants**: When adding or modifying the code follow the guidelines below:
- Avoid self-obvious comments that literally explain what the code does
- Follow existing patterns in the project when adding new code
- Add new files to git automatically
- Before continuing after a new user prompt, check and sync updated files to fetch the latest manual modifications
- Keep the changes minimal, avoid massive modifications
- Do not do extensive safety and null checks unless required by the business logic
- Put an empty line at the end of the files

## Overview
This is a Java Spring Boot application for web chat.

## Technology Stack
- **Framework**: Spring Boot 3.5.6
- **Java Version**: 21
- **Build Tool**: Gradle 8.5
- **Server Port**: 8080

## Build & Run
```bash
./gradlew bootRun    # run on :8080 (needs PostgreSQL on :5432)
./gradlew build      # compile + test
./gradlew test       # tests only
```

## Configuration
- `src/main/resources/application.yml` — Spring config, DB connection, Liquibase
- PostgreSQL: `localhost:5432`, db/user/password: `chatapp`
- Main class: `com.chatapp.ChatAppApplication`
- Base package: `com.chatapp`

## Key Packages
- `controller/` — AuthController, UserController (REST endpoints)
- `service/` — UserService (business logic)
- `entity/` — JPA entities (User)
- `repository/` — Spring Data JPA repositories
- `security/` — SessionAuthenticationFilter (session-based auth)
- `config/` — SecurityConfig, CorsConfig
- `dto/` — Request/response DTOs
- `exception/` — Global exception handling

## Database
- PostgreSQL 16 with Liquibase migrations
- Changelogs: `src/main/resources/db/changelog/changes/`
- Auth: session-based with BCrypt password hashing
