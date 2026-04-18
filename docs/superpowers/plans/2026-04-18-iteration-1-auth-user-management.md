# Iteration 1: Complete Auth & User Management — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can register with a unique username, manage their password, view/terminate sessions, and delete their account.

**Architecture:** Extend the existing session-based auth system. Replace `spring-session-core` with `spring-session-jdbc` for persistent, queryable sessions. Add `username` and `deleted_at` fields to the User entity. New endpoints for password change, account deletion, and session management. New Settings page on the frontend with tabbed UI.

**Tech Stack:** Spring Boot 3.5.6, spring-session-jdbc, PostgreSQL 16, Liquibase, React 19, Ant Design 5, TypeScript

---

## File Structure

### Backend — New Files

| File | Responsibility |
|------|---------------|
| `backend/src/main/java/com/chatapp/dto/ChangePasswordRequest.java` | DTO for password change |
| `backend/src/main/java/com/chatapp/dto/DeleteAccountRequest.java` | DTO for account deletion (password confirmation) |
| `backend/src/main/java/com/chatapp/dto/SessionResponse.java` | DTO for session info |
| `backend/src/main/java/com/chatapp/controller/SessionController.java` | Session list/invalidate endpoints |
| `backend/src/main/resources/db/changelog/changes/003-add-username-to-users.sql` | Add username + deleted_at columns |
| `backend/src/main/resources/db/changelog/changes/004-spring-session-tables.sql` | Spring Session JDBC tables |

### Backend — Modified Files

| File | Changes |
|------|---------|
| `backend/build.gradle` | Replace spring-session-core → spring-session-jdbc |
| `backend/src/main/resources/application.yml` | Add spring.session.store-type and jdbc config |
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | Include new migrations |
| `backend/src/main/java/com/chatapp/entity/User.java` | Add username, deletedAt fields |
| `backend/src/main/java/com/chatapp/repository/UserRepository.java` | Add existsByUsername query |
| `backend/src/main/java/com/chatapp/dto/SignUpRequest.java` | Add username validation |
| `backend/src/main/java/com/chatapp/dto/UserResponse.java` | Add username field |
| `backend/src/main/java/com/chatapp/service/UserService.java` | Username uniqueness, password change, soft-delete, reject deleted users |
| `backend/src/main/java/com/chatapp/config/SecurityConfig.java` | Remove maximumSessions(1), permit session endpoints |
| `backend/src/main/java/com/chatapp/security/SessionAuthenticationFilter.java` | Reject soft-deleted users |
| `backend/src/main/java/com/chatapp/controller/AuthController.java` | Set PRINCIPAL_NAME index on sign-in |
| `backend/src/main/java/com/chatapp/controller/UserController.java` | Signup creates session, add password change + delete account endpoints |

### Frontend — New Files

| File | Responsibility |
|------|---------------|
| `frontend/src/api/users.ts` | API functions for user management (password, delete, sessions) |
| `frontend/src/components/AppHeader.tsx` | Top navigation bar with user dropdown |
| `frontend/src/pages/Settings.tsx` | Tabbed settings page (Profile, Security, Sessions) |

### Frontend — Modified Files

| File | Changes |
|------|---------|
| `frontend/src/api/types.ts` | Add username to User, new request/response types |
| `frontend/src/pages/SignUp.tsx` | Add username field |
| `frontend/src/pages/Home.tsx` | Use AppHeader, show username |
| `frontend/src/App.tsx` | Add /settings route |
| `frontend/src/contexts/AuthContext.tsx` | Add updateUser helper |

---

## Task 1: Backend Dependencies & Configuration

**Files:**
- Modify: `backend/build.gradle:23`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Update build.gradle — replace spring-session-core with spring-session-jdbc**

In `backend/build.gradle`, change line 23:

```gradle
// OLD:
implementation 'org.springframework.session:spring-session-core'
// NEW:
implementation 'org.springframework.session:spring-session-jdbc'
```

- [ ] **Step 2: Update application.yml — add session JDBC config**

Add after the `liquibase` section in `backend/src/main/resources/application.yml`:

```yaml
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: never
```

The full `spring:` block should now be:

```yaml
spring:
  application:
    name: chatapp
  datasource:
    url: jdbc:postgresql://localhost:5432/chatapp
    username: chatapp
    password: chatapp
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  liquibase:
    change-log: classpath:/db/changelog/db.changelog-master.yaml
    enabled: true
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: never
```

- [ ] **Step 3: Commit**

```bash
git add backend/build.gradle backend/src/main/resources/application.yml
git commit -m "chore: replace spring-session-core with spring-session-jdbc"
```

---

## Task 2: Database Migrations

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/003-add-username-to-users.sql`
- Create: `backend/src/main/resources/db/changelog/changes/004-spring-session-tables.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration 003 — add username and deleted_at to users**

Create `backend/src/main/resources/db/changelog/changes/003-add-username-to-users.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:003-add-username-to-users
ALTER TABLE users ADD COLUMN username VARCHAR(32);

UPDATE users SET username = 'alice' WHERE email = 'alice@demo.com';
UPDATE users SET username = 'bob' WHERE email = 'bob@demo.com';
UPDATE users SET username = 'carol' WHERE email = 'carol@demo.com';
UPDATE users SET username = 'dave' WHERE email = 'dave@demo.com';
UPDATE users SET username = 'eve' WHERE email = 'eve@demo.com';
UPDATE users SET username = 'frank' WHERE email = 'frank@demo.com';

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username);

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
```

- [ ] **Step 2: Create migration 004 — Spring Session JDBC tables**

Create `backend/src/main/resources/db/changelog/changes/004-spring-session-tables.sql`:

```sql
--liquibase formatted sql

--changeset chatapp:004-spring-session-tables
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
```

- [ ] **Step 3: Update changelog master to include new migrations**

Append to `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-users-table.sql
  - include:
      file: db/changelog/changes/002-insert-demo-data.sql
  - include:
      file: db/changelog/changes/003-add-username-to-users.sql
  - include:
      file: db/changelog/changes/004-spring-session-tables.sql
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat: add username/deleted_at columns and spring session tables"
```

---

## Task 3: User Entity & Repository

**Files:**
- Modify: `backend/src/main/java/com/chatapp/entity/User.java`
- Modify: `backend/src/main/java/com/chatapp/repository/UserRepository.java`

- [ ] **Step 1: Add username and deletedAt fields to User entity**

In `backend/src/main/java/com/chatapp/entity/User.java`, add two fields after `email`:

```java
@Column(nullable = false, unique = true, updatable = false, length = 32)
private String username;
```

And after `updatedAt`:

```java
@Column(name = "deleted_at")
private Instant deletedAt;
```

Full entity should be:

```java
package com.chatapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, updatable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}
```

- [ ] **Step 2: Add existsByUsername to UserRepository**

In `backend/src/main/java/com/chatapp/repository/UserRepository.java`, add:

```java
boolean existsByUsername(String username);
```

Full file:

```java
package com.chatapp.repository;

import com.chatapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/entity/User.java backend/src/main/java/com/chatapp/repository/UserRepository.java
git commit -m "feat: add username and deletedAt to User entity and repository"
```

---

## Task 4: DTOs — New and Updated

**Files:**
- Modify: `backend/src/main/java/com/chatapp/dto/SignUpRequest.java`
- Modify: `backend/src/main/java/com/chatapp/dto/UserResponse.java`
- Create: `backend/src/main/java/com/chatapp/dto/ChangePasswordRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/DeleteAccountRequest.java`
- Create: `backend/src/main/java/com/chatapp/dto/SessionResponse.java`

- [ ] **Step 1: Add username to SignUpRequest**

Replace `backend/src/main/java/com/chatapp/dto/SignUpRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

}
```

- [ ] **Step 2: Add username to UserResponse**

Replace `backend/src/main/java/com/chatapp/dto/UserResponse.java`:

```java
package com.chatapp.dto;

import com.chatapp.entity.User;
import lombok.Data;
import java.time.Instant;

@Data
public class UserResponse {

    private Long id;
    private String email;
    private String username;
    private String displayName;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserResponse fromEntity(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

}
```

- [ ] **Step 3: Create ChangePasswordRequest**

Create `backend/src/main/java/com/chatapp/dto/ChangePasswordRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

}
```

- [ ] **Step 4: Create DeleteAccountRequest**

Create `backend/src/main/java/com/chatapp/dto/DeleteAccountRequest.java`:

```java
package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    @NotBlank(message = "Password is required to confirm account deletion")
    private String password;

}
```

- [ ] **Step 5: Create SessionResponse**

Create `backend/src/main/java/com/chatapp/dto/SessionResponse.java`:

```java
package com.chatapp.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SessionResponse {

    private String sessionId;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private boolean current;

}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/chatapp/dto/
git commit -m "feat: add username to DTOs, create ChangePassword/DeleteAccount/Session DTOs"
```

---

## Task 5: UserService Updates

**Files:**
- Modify: `backend/src/main/java/com/chatapp/service/UserService.java`

- [ ] **Step 1: Update UserService with all new functionality**

Replace `backend/src/main/java/com/chatapp/service/UserService.java`:

```java
package com.chatapp.service;

import com.chatapp.dto.ChangePasswordRequest;
import com.chatapp.dto.SignInRequest;
import com.chatapp.dto.SignUpRequest;
import com.chatapp.entity.User;
import com.chatapp.exception.InvalidCredentialsException;
import com.chatapp.exception.UserAlreadyExistsException;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("User with this username already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());

        return userRepository.save(user);
    }

    public User signIn(SignInRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getDeletedAt() != null) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return user;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = findById(userId);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Password is incorrect");
        }

        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/service/UserService.java
git commit -m "feat: add password change, account deletion, and deleted-user rejection to UserService"
```

---

## Task 6: Security Updates

**Files:**
- Modify: `backend/src/main/java/com/chatapp/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/chatapp/security/SessionAuthenticationFilter.java`

- [ ] **Step 1: Update SecurityConfig — remove maximumSessions, permit new endpoints**

Replace `backend/src/main/java/com/chatapp/config/SecurityConfig.java`:

```java
package com.chatapp.config;

import com.chatapp.security.SessionAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final SessionAuthenticationFilter sessionAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/users/signup", "/api/auth/signin", "/api/auth/me").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );

        return http.build();
    }

}
```

Key change: removed `.maximumSessions(1)`.

- [ ] **Step 2: Update SessionAuthenticationFilter — reject soft-deleted users**

Replace `backend/src/main/java/com/chatapp/security/SessionAuthenticationFilter.java`:

```java
package com.chatapp.security;

import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;
    private static final String SESSION_USER_KEY = "user_id";

    public SessionAuthenticationFilter(@Lazy UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null) {
            Long userId = (Long) session.getAttribute(SESSION_USER_KEY);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    User user = userService.findById(userId);

                    if (user.getDeletedAt() != null) {
                        session.invalidate();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getId(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    session.invalidate();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

}
```

Key changes:
- Reject soft-deleted users (check `deletedAt`, invalidate session)
- Store `user.getId()` as the principal (was `user.getEmail()`) — needed for extracting user ID from SecurityContext in controllers

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/config/SecurityConfig.java backend/src/main/java/com/chatapp/security/SessionAuthenticationFilter.java
git commit -m "feat: remove session limit, reject soft-deleted users in auth filter"
```

---

## Task 7: AuthController & UserController Updates

**Files:**
- Modify: `backend/src/main/java/com/chatapp/controller/AuthController.java`
- Modify: `backend/src/main/java/com/chatapp/controller/UserController.java`

- [ ] **Step 1: Update AuthController — set PRINCIPAL_NAME on sign-in**

Replace `backend/src/main/java/com/chatapp/controller/AuthController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.SignInRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private static final String SESSION_USER_KEY = "user_id";

    @PostMapping("/signin")
    public ResponseEntity<UserResponse> signIn(
            @Valid @RequestBody SignInRequest request,
            HttpSession session) {
        User user = userService.signIn(request);
        session.setAttribute(SESSION_USER_KEY, user.getId());
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                user.getEmail()
        );
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_KEY);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            User user = userService.findById(userId);
            if (user.getDeletedAt() != null) {
                session.invalidate();
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.ok(UserResponse.fromEntity(user));
        } catch (Exception e) {
            session.invalidate();
            return ResponseEntity.status(401).build();
        }
    }

}
```

Key change: setting `PRINCIPAL_NAME_INDEX_NAME` session attribute — required for `FindByIndexNameSessionRepository` to query sessions by user.

- [ ] **Step 2: Update UserController — signup creates session, add password/delete endpoints**

Replace `backend/src/main/java/com/chatapp/controller/UserController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.ChangePasswordRequest;
import com.chatapp.dto.DeleteAccountRequest;
import com.chatapp.dto.SignUpRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    private static final String SESSION_USER_KEY = "user_id";

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(
            @Valid @RequestBody SignUpRequest request,
            HttpSession session) {
        User user = userService.signUp(request);
        session.setAttribute(SESSION_USER_KEY, user.getId());
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                user.getEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(user));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication,
            HttpSession session) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        userService.deleteAccount(userId, request.getPassword());

        // Invalidate all sessions for this user
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());
        sessions.keySet().forEach(sessionRepository::deleteById);

        return ResponseEntity.ok().build();
    }

}
```

Key changes:
- `signUp` now takes `HttpSession` and creates a session (sets `user_id` + principal name)
- New `PUT /me/password` endpoint extracts user ID from `Authentication.getPrincipal()`
- New `DELETE /me` endpoint soft-deletes account and invalidates all sessions

- [ ] **Step 3: Verify it compiles**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/
git commit -m "feat: signup creates session, add password change and account deletion endpoints"
```

---

## Task 8: Session Management Controller

**Files:**
- Create: `backend/src/main/java/com/chatapp/controller/SessionController.java`

- [ ] **Step 1: Create SessionController**

Create `backend/src/main/java/com/chatapp/controller/SessionController.java`:

```java
package com.chatapp.controller;

import com.chatapp.dto.SessionResponse;
import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions(
            Authentication authentication,
            HttpSession currentSession) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());

        List<SessionResponse> responses = sessions.entrySet().stream()
                .map(entry -> {
                    Session session = entry.getValue();
                    SessionResponse response = new SessionResponse();
                    response.setSessionId(session.getId());
                    response.setCreatedAt(session.getCreationTime());
                    response.setLastAccessedAt(session.getLastAccessedTime());
                    response.setCurrent(session.getId().equals(currentSession.getId()));
                    return response;
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{sessionId}/invalidate")
    public ResponseEntity<Void> invalidateSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        // Verify the session belongs to this user
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());

        if (!sessions.containsKey(sessionId)) {
            return ResponseEntity.notFound().build();
        }

        sessionRepository.deleteById(sessionId);
        return ResponseEntity.ok().build();
    }

}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/chatapp/controller/SessionController.java
git commit -m "feat: add session list and invalidation endpoints"
```

---

## Task 9: Build & Verify Backend

- [ ] **Step 1: Build the full backend**

Run: `cd backend && ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Rebuild and start Docker Compose**

Run: `docker-compose down -v && docker-compose up -d --build`
Wait for all containers to be healthy.

- [ ] **Step 3: Verify signup with username**

```bash
curl -s -c cookies.txt -X POST http://localhost:8080/api/users/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","username":"testuser","password":"password123","displayName":"Test User"}'
```

Expected: 201 response with `username` field in JSON.

- [ ] **Step 4: Verify session was created on signup**

```bash
curl -s -b cookies.txt http://localhost:8080/api/auth/me
```

Expected: 200 response with user data (proves session was created during signup).

- [ ] **Step 5: Verify password change**

```bash
curl -s -b cookies.txt -X PUT http://localhost:8080/api/users/me/password \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"password123","newPassword":"newpassword123"}'
```

Expected: 200 OK.

- [ ] **Step 6: Verify session listing**

```bash
curl -s -b cookies.txt http://localhost:8080/api/users/me/sessions
```

Expected: 200 with array containing at least one session with `current: true`.

- [ ] **Step 7: Commit (if any fixes were needed)**

```bash
git add -A && git commit -m "fix: address issues found during backend verification"
```

Only commit if changes were made. Skip if everything passed.

---

## Task 10: Frontend Types & API Layer

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/users.ts`

- [ ] **Step 1: Update types.ts — add username and new interfaces**

Replace `frontend/src/api/types.ts`:

```typescript
export interface User {
  id: number;
  email: string;
  username: string;
  displayName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SignUpRequest {
  email: string;
  username: string;
  password: string;
  displayName: string;
}

export interface SignInRequest {
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface DeleteAccountRequest {
  password: string;
}

export interface SessionInfo {
  sessionId: string;
  createdAt: string;
  lastAccessedAt: string;
  current: boolean;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
```

- [ ] **Step 2: Create users.ts API module**

Create `frontend/src/api/users.ts`:

```typescript
import client from './client.ts';
import type { ChangePasswordRequest, DeleteAccountRequest, SessionInfo } from './types.ts';

export const usersApi = {
  changePassword: async (data: ChangePasswordRequest): Promise<void> => {
    await client.put('/users/me/password', data);
  },

  deleteAccount: async (data: DeleteAccountRequest): Promise<void> => {
    await client.delete('/users/me', { data });
  },

  getSessions: async (): Promise<SessionInfo[]> => {
    const response = await client.get<SessionInfo[]>('/users/me/sessions');
    return response.data;
  },

  terminateSession: async (sessionId: string): Promise<void> => {
    await client.post(`/users/me/sessions/${sessionId}/invalidate`);
  },
};
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/users.ts
git commit -m "feat: add username to types, create users API module"
```

---

## Task 11: Frontend — AppHeader Component

**Files:**
- Create: `frontend/src/components/AppHeader.tsx`

- [ ] **Step 1: Create AppHeader**

Create `frontend/src/components/AppHeader.tsx`:

```tsx
import { Layout, Dropdown, Button, Typography } from 'antd';
import { UserOutlined, SettingOutlined, LogoutOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.tsx';
import type { MenuProps } from 'antd';

const { Header } = Layout;
const { Text } = Typography;

export const AppHeader = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/signin');
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: 'Settings',
      onClick: () => navigate('/settings'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      onClick: handleLogout,
    },
  ];

  return (
    <Header style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      background: '#fff',
      padding: '0 24px',
      borderBottom: '1px solid #f0f0f0',
    }}>
      <Text strong style={{ fontSize: 18 }}>ChatApp</Text>
      <Dropdown menu={{ items: menuItems }} placement="bottomRight">
        <Button icon={<UserOutlined />}>
          {user?.displayName || user?.username}
        </Button>
      </Dropdown>
    </Header>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/AppHeader.tsx
git commit -m "feat: create AppHeader component with user dropdown menu"
```

---

## Task 12: Frontend — Settings Page

**Files:**
- Create: `frontend/src/pages/Settings.tsx`

- [ ] **Step 1: Create Settings page with Profile, Security, and Sessions tabs**

Create `frontend/src/pages/Settings.tsx`:

```tsx
import { useState, useEffect } from 'react';
import {
  Tabs, Form, Input, Button, Card, Typography, message, Modal, Table, Tag,
} from 'antd';
import {
  KeyOutlined, DeleteOutlined, ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.tsx';
import { usersApi } from '../api/users.ts';
import type { SessionInfo } from '../api/types.ts';

const { Title, Text } = Typography;

const ProfileTab = () => {
  const { user } = useAuth();

  return (
    <div style={{ maxWidth: 400 }}>
      <Form layout="vertical">
        <Form.Item label="Username">
          <Input value={user?.username} disabled />
        </Form.Item>
        <Form.Item label="Email">
          <Input value={user?.email} disabled />
        </Form.Item>
        <Form.Item label="Display Name">
          <Input value={user?.displayName || ''} disabled />
        </Form.Item>
      </Form>
      <Text type="secondary">Profile editing will be available in a future update.</Text>
    </div>
  );
};

const SecurityTab = () => {
  const [passwordForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleChangePassword = async (values: { currentPassword: string; newPassword: string }) => {
    setLoading(true);
    try {
      await usersApi.changePassword(values);
      message.success('Password changed successfully');
      passwordForm.resetFields();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAccount = () => {
    let password = '';
    Modal.confirm({
      title: 'Delete Account',
      icon: <ExclamationCircleOutlined />,
      content: (
        <div>
          <p>This action cannot be undone. Enter your password to confirm:</p>
          <Input.Password
            onChange={(e) => { password = e.target.value; }}
            placeholder="Enter your password"
          />
        </div>
      ),
      okText: 'Delete Account',
      okType: 'danger',
      onOk: async () => {
        if (!password) {
          message.error('Password is required');
          throw new Error('Password required');
        }
        setDeleteLoading(true);
        try {
          await usersApi.deleteAccount({ password });
          message.success('Account deleted');
          await logout();
          navigate('/signin');
        } catch (error: unknown) {
          const err = error as { response?: { data?: { message?: string } } };
          message.error(err.response?.data?.message || 'Failed to delete account');
          throw error;
        } finally {
          setDeleteLoading(false);
        }
      },
    });
  };

  return (
    <div style={{ maxWidth: 400 }}>
      <Title level={5}>Change Password</Title>
      <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
        <Form.Item
          name="currentPassword"
          label="Current Password"
          rules={[{ required: true, message: 'Please enter your current password' }]}
        >
          <Input.Password prefix={<KeyOutlined />} />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="New Password"
          rules={[
            { required: true, message: 'Please enter a new password' },
            { min: 8, message: 'Password must be at least 8 characters' },
          ]}
        >
          <Input.Password prefix={<KeyOutlined />} />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="Confirm New Password"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Please confirm your new password' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('Passwords do not match'));
              },
            }),
          ]}
        >
          <Input.Password prefix={<KeyOutlined />} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>
            Change Password
          </Button>
        </Form.Item>
      </Form>

      <div style={{ marginTop: 48, paddingTop: 24, borderTop: '1px solid #f0f0f0' }}>
        <Title level={5} type="danger">Danger Zone</Title>
        <Button
          danger
          icon={<DeleteOutlined />}
          onClick={handleDeleteAccount}
          loading={deleteLoading}
        >
          Delete Account
        </Button>
      </div>
    </div>
  );
};

const SessionsTab = () => {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchSessions = async () => {
    setLoading(true);
    try {
      const data = await usersApi.getSessions();
      setSessions(data);
    } catch {
      message.error('Failed to load sessions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleTerminate = async (sessionId: string) => {
    try {
      await usersApi.terminateSession(sessionId);
      message.success('Session terminated');
      fetchSessions();
    } catch {
      message.error('Failed to terminate session');
    }
  };

  const columns = [
    {
      title: 'Session',
      dataIndex: 'sessionId',
      key: 'sessionId',
      render: (id: string, record: SessionInfo) => (
        <span>
          {id.substring(0, 8)}...
          {record.current && <Tag color="green" style={{ marginLeft: 8 }}>Current</Tag>}
        </span>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: 'Last Active',
      dataIndex: 'lastAccessedAt',
      key: 'lastAccessedAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, record: SessionInfo) => (
        !record.current && (
          <Button
            size="small"
            danger
            onClick={() => handleTerminate(record.sessionId)}
          >
            Terminate
          </Button>
        )
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={sessions}
      rowKey="sessionId"
      loading={loading}
      pagination={false}
    />
  );
};

export const Settings = () => {
  const items = [
    { key: 'profile', label: 'Profile', children: <ProfileTab /> },
    { key: 'security', label: 'Security', children: <SecurityTab /> },
    { key: 'sessions', label: 'Sessions', children: <SessionsTab /> },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 800, margin: '0 auto' }}>
      <Title level={3}>Settings</Title>
      <Card>
        <Tabs items={items} />
      </Card>
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/Settings.tsx
git commit -m "feat: create Settings page with Profile, Security, and Sessions tabs"
```

---

## Task 13: Frontend — Update SignUp, Home, App Routes

**Files:**
- Modify: `frontend/src/pages/SignUp.tsx`
- Modify: `frontend/src/pages/Home.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Add username field to SignUp page**

In `frontend/src/pages/SignUp.tsx`, add a username field after the email field. The `onFinish` handler signature also needs `username`. Replace the full file:

```tsx
import { useState } from 'react';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, IdcardOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/auth.ts';
import { useAuth } from '../contexts/AuthContext.tsx';

const { Title } = Typography;

export const SignUp = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const onFinish = async (values: { email: string; username: string; password: string; displayName: string }) => {
    setLoading(true);
    try {
      const user = await authApi.signUp(values);
      message.success('Account created successfully!');
      login(user);
      navigate('/');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Sign up failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: '#f0f2f5' }}>
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <Card style={{ width: 400 }}>
          <Title level={2} style={{ textAlign: 'center' }}>Sign Up</Title>
          <Form
            name="signup"
            onFinish={onFinish}
            autoComplete="off"
            layout="vertical"
          >
            <Form.Item
              name="email"
              rules={[
                { required: true, message: 'Please input your email!' },
                { type: 'email', message: 'Please enter a valid email!' }
              ]}
            >
              <Input prefix={<MailOutlined />} placeholder="Email" />
            </Form.Item>

            <Form.Item
              name="username"
              rules={[
                { required: true, message: 'Please input your username!' },
                { min: 3, message: 'Username must be at least 3 characters!' },
                { max: 32, message: 'Username must not exceed 32 characters!' },
                { pattern: /^[a-zA-Z0-9_]+$/, message: 'Only letters, numbers, and underscores!' }
              ]}
            >
              <Input prefix={<IdcardOutlined />} placeholder="Username" />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: 'Please input your password!' },
                { min: 8, message: 'Password must be at least 8 characters!' }
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="Password" />
            </Form.Item>

            <Form.Item
              name="displayName"
              rules={[{ required: true, message: 'Please input your display name!' }]}
            >
              <Input prefix={<UserOutlined />} placeholder="Display Name" />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>
                Sign Up
              </Button>
            </Form.Item>

            <div style={{ textAlign: 'center' }}>
              Already have an account? <Link to="/signin">Sign In</Link>
            </div>
          </Form>
        </Card>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Update Home page to use AppHeader**

Replace `frontend/src/pages/Home.tsx`:

```tsx
import { Typography } from 'antd';
import { useAuth } from '../contexts/AuthContext.tsx';
import { AppHeader } from '../components/AppHeader.tsx';

const { Title } = Typography;

export const Home = () => {
  const { user } = useAuth();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', backgroundColor: '#f0f2f5' }}>
        <Title>Hello {user?.displayName || user?.username}</Title>
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Add /settings route to App.tsx**

Replace `frontend/src/App.tsx`:

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext.tsx';
import { ProtectedRoute } from './components/ProtectedRoute.tsx';
import { SignUp } from './pages/SignUp.tsx';
import { SignIn } from './pages/SignIn.tsx';
import { Home } from './pages/Home.tsx';
import { Settings } from './pages/Settings.tsx';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/signup" element={<SignUp />} />
          <Route path="/signin" element={<SignIn />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Home />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <Settings />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
```

- [ ] **Step 4: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/SignUp.tsx frontend/src/pages/Home.tsx frontend/src/App.tsx
git commit -m "feat: add username to signup, integrate AppHeader, add settings route"
```

---

## Task 14: End-to-End Verification

- [ ] **Step 1: Rebuild and start all services**

```bash
docker-compose down -v && docker-compose up -d --build
```

Wait for all containers to be healthy.

- [ ] **Step 2: Verify full flow in browser**

Open `http://localhost:5173` and test:

1. **Sign Up** — register with email, username, password, display name → should auto-login and redirect to Home
2. **Home** — should show AppHeader with user dropdown
3. **Settings** — click Settings in dropdown → should show tabbed page
4. **Profile tab** — should show read-only username, email, display name
5. **Sessions tab** — should show current session with "Current" tag
6. **Security tab** — change password → should succeed
7. **Demo login** — sign in as a demo user (alice@demo.com / password) → should work
8. **Sign out** — should redirect to sign-in page

- [ ] **Step 3: Fix any issues found**

If any issues are found, fix them and commit:

```bash
git add -A && git commit -m "fix: address issues found during e2e verification"
```
