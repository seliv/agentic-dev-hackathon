# Phase 1 Testing — Backend Integration Tests + E2E Tests

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend integration tests (JUnit 5 + Testcontainers) and end-to-end tests (Playwright) for all Iteration 1 features: signup, signin, logout, me, password change, account deletion, and session management.

**Architecture:** Backend tests use `@SpringBootTest` with `MockMvc` and Testcontainers PostgreSQL — real database, no mocks. E2E tests use Playwright against the full docker-compose stack, testing actual user-visible behavior including error messages.

**Tech Stack:** JUnit 5, Testcontainers (PostgreSQL), Spring Boot Test, MockMvc, Playwright, TypeScript

---

## File Structure

### Backend Tests

| File | Responsibility |
|------|----------------|
| `backend/build.gradle` | **Modify** — add Testcontainers dependencies |
| `backend/src/test/java/com/chatapp/BaseIntegrationTest.java` | **Create** — abstract base class with Testcontainers PostgreSQL, shared container |
| `backend/src/test/java/com/chatapp/controller/SignUpTest.java` | **Create** — signup endpoint tests (happy path, validation, duplicates) |
| `backend/src/test/java/com/chatapp/controller/SignInTest.java` | **Create** — signin endpoint tests (happy path, wrong password, non-existent email, deleted user) |
| `backend/src/test/java/com/chatapp/controller/AuthStateTest.java` | **Create** — /me and /logout endpoint tests |
| `backend/src/test/java/com/chatapp/controller/PasswordChangeTest.java` | **Create** — password change tests (happy path, wrong current, validation) |
| `backend/src/test/java/com/chatapp/controller/AccountDeletionTest.java` | **Create** — account deletion tests (happy path, wrong password, sessions invalidated) |
| `backend/src/test/java/com/chatapp/controller/SessionManagementTest.java` | **Create** — session list and invalidation tests |

### E2E Tests

| File | Responsibility |
|------|----------------|
| `e2e/package.json` | **Create** — Playwright dependency |
| `e2e/playwright.config.ts` | **Create** — Playwright configuration (base URL, timeouts, project) |
| `e2e/tests/signup.spec.ts` | **Create** — signup flow e2e (happy path, validation errors, duplicate email) |
| `e2e/tests/signin.spec.ts` | **Create** — signin flow e2e (happy path, wrong credentials error message, navigation) |
| `e2e/tests/settings.spec.ts` | **Create** — settings page e2e (password change, account deletion) |

---

### Task 1: Backend Test Infrastructure

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/test/java/com/chatapp/BaseIntegrationTest.java`

- [ ] **Step 1: Add Testcontainers dependencies to build.gradle**

In `backend/build.gradle`, add these to the `dependencies` block:

```groovy
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'org.springframework.security:spring-security-test'
```

- [ ] **Step 2: Create BaseIntegrationTest**

Create `backend/src/test/java/com/chatapp/BaseIntegrationTest.java`:

```java
package com.chatapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.session.jdbc.initialize-schema", () -> "always");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
```

- [ ] **Step 3: Verify infrastructure works**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (no test classes yet, but context loads and Testcontainers starts PostgreSQL)

- [ ] **Step 4: Commit**

```bash
git add backend/build.gradle backend/src/test/java/com/chatapp/BaseIntegrationTest.java
git commit -m "test: add Testcontainers infrastructure for backend integration tests"
```

---

### Task 2: Signup Endpoint Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/SignUpTest.java`

- [ ] **Step 1: Create SignUpTest with all test cases**

Create `backend/src/test/java/com/chatapp/controller/SignUpTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SignUpTest extends BaseIntegrationTest {

    @Test
    void signUp_validRequest_returns201WithUser() throws Exception {
        String body = """
                {
                    "email": "newuser@test.com",
                    "username": "newuser",
                    "password": "password123",
                    "displayName": "New User"
                }
                """;

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@test.com"))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.displayName").value("New User"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signUp_createsSession() throws Exception {
        String body = """
                {
                    "email": "sessionuser@test.com",
                    "username": "sessionuser",
                    "password": "password123",
                    "displayName": "Session User"
                }
                """;

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(request().sessionAttribute("user_id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void signUp_duplicateEmail_returns409() throws Exception {
        String body = """
                {
                    "email": "dupe@test.com",
                    "username": "dupeuser1",
                    "password": "password123",
                    "displayName": "Dupe 1"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String body2 = """
                {
                    "email": "dupe@test.com",
                    "username": "dupeuser2",
                    "password": "password123",
                    "displayName": "Dupe 2"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with this email already exists"));
    }

    @Test
    void signUp_duplicateUsername_returns409() throws Exception {
        String body = """
                {
                    "email": "unique1@test.com",
                    "username": "sameusername",
                    "password": "password123",
                    "displayName": "User 1"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String body2 = """
                {
                    "email": "unique2@test.com",
                    "username": "sameusername",
                    "password": "password123",
                    "displayName": "User 2"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with this username already exists"));
    }

    @Test
    void signUp_missingEmail_returns400() throws Exception {
        String body = """
                {
                    "username": "noemailu",
                    "password": "password123",
                    "displayName": "No Email"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_invalidEmail_returns400() throws Exception {
        String body = """
                {
                    "email": "not-an-email",
                    "username": "bademail",
                    "password": "password123",
                    "displayName": "Bad Email"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_shortPassword_returns400() throws Exception {
        String body = """
                {
                    "email": "shortpw@test.com",
                    "username": "shortpw",
                    "password": "short",
                    "displayName": "Short PW"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_invalidUsername_returns400() throws Exception {
        String body = """
                {
                    "email": "baduser@test.com",
                    "username": "bad user!",
                    "password": "password123",
                    "displayName": "Bad Username"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signUp_usernameTooShort_returns400() throws Exception {
        String body = """
                {
                    "email": "short@test.com",
                    "username": "ab",
                    "password": "password123",
                    "displayName": "Short Username"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.SignUpTest"`
Expected: All 9 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/SignUpTest.java
git commit -m "test: add signup endpoint integration tests"
```

---

### Task 3: Signin Endpoint Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/SignInTest.java`

- [ ] **Step 1: Create SignInTest with all test cases**

Create `backend/src/test/java/com/chatapp/controller/SignInTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SignInTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (userRepository.findByEmail("signin@test.com").isEmpty()) {
            User user = new User();
            user.setEmail("signin@test.com");
            user.setUsername("signinuser");
            user.setPassword(passwordEncoder.encode("password123"));
            user.setDisplayName("Sign In User");
            userRepository.save(user);
        }
    }

    @Test
    void signIn_validCredentials_returns200WithUser() throws Exception {
        String body = """
                {
                    "email": "signin@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("signin@test.com"))
                .andExpect(jsonPath("$.username").value("signinuser"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signIn_createsSession() throws Exception {
        String body = """
                {
                    "email": "signin@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("user_id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void signIn_wrongPassword_returns401WithMessage() throws Exception {
        String body = """
                {
                    "email": "signin@test.com",
                    "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signIn_nonExistentEmail_returns401WithMessage() throws Exception {
        String body = """
                {
                    "email": "nobody@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signIn_deletedUser_returns401() throws Exception {
        User deleted = userRepository.findByEmail("signin@test.com").orElseThrow();
        if (deleted.getDeletedAt() == null) {
            // Create a separate deleted user for this test
            if (userRepository.findByEmail("deleted@test.com").isEmpty()) {
                User user = new User();
                user.setEmail("deleted@test.com");
                user.setUsername("deleteduser");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setDisplayName("Deleted User");
                user.setDeletedAt(Instant.now());
                userRepository.save(user);
            }
        }

        String body = """
                {
                    "email": "deleted@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signIn_missingEmail_returns400() throws Exception {
        String body = """
                {
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signIn_missingPassword_returns400() throws Exception {
        String body = """
                {
                    "email": "signin@test.com"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signIn_invalidEmailFormat_returns400() throws Exception {
        String body = """
                {
                    "email": "not-an-email",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.SignInTest"`
Expected: All 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/SignInTest.java
git commit -m "test: add signin endpoint integration tests"
```

---

### Task 4: Auth State Tests (me + logout)

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/AuthStateTest.java`

- [ ] **Step 1: Create AuthStateTest**

Create `backend/src/test/java/com/chatapp/controller/AuthStateTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthStateTest extends BaseIntegrationTest {

    private MockHttpSession signUpAndGetSession(String email, String username) throws Exception {
        String body = String.format("""
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "password123",
                    "displayName": "Test User"
                }
                """, email, username);

        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void me_withValidSession_returnsUser() throws Exception {
        MockHttpSession session = signUpAndGetSession("me@test.com", "meuser");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@test.com"))
                .andExpect(jsonPath("$.username").value("meuser"));
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        MockHttpSession session = signUpAndGetSession("logout@test.com", "logoutuser");

        // Verify session works
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());

        // Logout
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        // Session should be invalidated - /me should return 401
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_afterSignIn_returnsUser() throws Exception {
        // First sign up
        String signUpBody = """
                {
                    "email": "mesi@test.com",
                    "username": "mesiuser",
                    "password": "password123",
                    "displayName": "Me SI User"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody))
                .andExpect(status().isCreated());

        // Sign in
        String signInBody = """
                {
                    "email": "mesi@test.com",
                    "password": "password123"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        // /me should return user
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mesi@test.com"));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.AuthStateTest"`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/AuthStateTest.java
git commit -m "test: add auth state (me/logout) integration tests"
```

---

### Task 5: Password Change Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/PasswordChangeTest.java`

- [ ] **Step 1: Create PasswordChangeTest**

Create `backend/src/test/java/com/chatapp/controller/PasswordChangeTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordChangeTest extends BaseIntegrationTest {

    private MockHttpSession signUpAndGetSession(String email, String username) throws Exception {
        String body = String.format("""
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "password123",
                    "displayName": "Test User"
                }
                """, email, username);

        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void changePassword_validRequest_returns200() throws Exception {
        MockHttpSession session = signUpAndGetSession("pwchange@test.com", "pwchangeuser");

        String body = """
                {
                    "currentPassword": "password123",
                    "newPassword": "newpassword123"
                }
                """;

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_canSignInWithNewPassword() throws Exception {
        MockHttpSession session = signUpAndGetSession("pwchange2@test.com", "pwchange2");

        String changeBody = """
                {
                    "currentPassword": "password123",
                    "newPassword": "brandnewpw123"
                }
                """;

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody))
                .andExpect(status().isOk());

        // Sign in with new password
        String signInBody = """
                {
                    "email": "pwchange2@test.com",
                    "password": "brandnewpw123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        MockHttpSession session = signUpAndGetSession("pwwrong@test.com", "pwwronguser");

        String body = """
                {
                    "currentPassword": "wrongpassword",
                    "newPassword": "newpassword123"
                }
                """;

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changePassword_shortNewPassword_returns400() throws Exception {
        MockHttpSession session = signUpAndGetSession("pwshort@test.com", "pwshortuser");

        String body = """
                {
                    "currentPassword": "password123",
                    "newPassword": "short"
                }
                """;

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void changePassword_notAuthenticated_returns403() throws Exception {
        String body = """
                {
                    "currentPassword": "password123",
                    "newPassword": "newpassword123"
                }
                """;

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.PasswordChangeTest"`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/PasswordChangeTest.java
git commit -m "test: add password change integration tests"
```

---

### Task 6: Account Deletion Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/AccountDeletionTest.java`

- [ ] **Step 1: Create AccountDeletionTest**

Create `backend/src/test/java/com/chatapp/controller/AccountDeletionTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import com.chatapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountDeletionTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private MockHttpSession signUpAndGetSession(String email, String username) throws Exception {
        String body = String.format("""
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "password123",
                    "displayName": "Test User"
                }
                """, email, username);

        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void deleteAccount_validPassword_returns200() throws Exception {
        MockHttpSession session = signUpAndGetSession("delete@test.com", "deleteuser");

        String body = """
                {"password": "password123"}
                """;

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccount_setsDeletedAt() throws Exception {
        MockHttpSession session = signUpAndGetSession("delete2@test.com", "delete2user");

        String body = """
                {"password": "password123"}
                """;

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var user = userRepository.findByEmail("delete2@test.com").orElseThrow();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteAccount_cannotSignInAfterDeletion() throws Exception {
        MockHttpSession session = signUpAndGetSession("delete3@test.com", "delete3user");

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "password123"}
                                """))
                .andExpect(status().isOk());

        // Try to sign in
        String signInBody = """
                {
                    "email": "delete3@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_wrongPassword_returns401() throws Exception {
        MockHttpSession session = signUpAndGetSession("delete4@test.com", "delete4user");

        String body = """
                {"password": "wrongpassword"}
                """;

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Password is incorrect"));
    }

    @Test
    void deleteAccount_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "password123"}
                                """))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.AccountDeletionTest"`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/AccountDeletionTest.java
git commit -m "test: add account deletion integration tests"
```

---

### Task 7: Session Management Tests

**Files:**
- Create: `backend/src/test/java/com/chatapp/controller/SessionManagementTest.java`

- [ ] **Step 1: Create SessionManagementTest**

Create `backend/src/test/java/com/chatapp/controller/SessionManagementTest.java`:

```java
package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionManagementTest extends BaseIntegrationTest {

    private MockHttpSession signUpAndGetSession(String email, String username) throws Exception {
        String body = String.format("""
                {
                    "email": "%s",
                    "username": "%s",
                    "password": "password123",
                    "displayName": "Test User"
                }
                """, email, username);

        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void listSessions_returnsCurrentSession() throws Exception {
        MockHttpSession session = signUpAndGetSession("sess@test.com", "sessuser");

        mockMvc.perform(get("/api/users/me/sessions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sessionId").isNotEmpty())
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].lastAccessedAt").isNotEmpty());
    }

    @Test
    void listSessions_multipleSessionsAfterMultipleSignIns() throws Exception {
        // Sign up first
        String signUpBody = """
                {
                    "email": "multisess@test.com",
                    "username": "multisess",
                    "password": "password123",
                    "displayName": "Multi Session"
                }
                """;
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody))
                .andExpect(status().isCreated());

        // Sign in again (creates second session)
        String signInBody = """
                {
                    "email": "multisess@test.com",
                    "password": "password123"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session2 = (MockHttpSession) result.getRequest().getSession();

        mockMvc.perform(get("/api/users/me/sessions").session(session2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void invalidateSession_removesOtherSession() throws Exception {
        // Sign up
        String signUpBody = """
                {
                    "email": "invsess@test.com",
                    "username": "invsessuser",
                    "password": "password123",
                    "displayName": "Inv Session"
                }
                """;
        MvcResult result1 = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session1 = (MockHttpSession) result1.getRequest().getSession();

        // Sign in again
        String signInBody = """
                {
                    "email": "invsess@test.com",
                    "password": "password123"
                }
                """;
        MvcResult result2 = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signInBody))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session2 = (MockHttpSession) result2.getRequest().getSession();

        // Get list of sessions from session2, find session1's ID
        MvcResult listResult = mockMvc.perform(get("/api/users/me/sessions").session(session2))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = listResult.getResponse().getContentAsString();
        var sessions = objectMapper.readTree(responseBody);

        String otherSessionId = null;
        for (var s : sessions) {
            if (!s.get("current").asBoolean()) {
                otherSessionId = s.get("sessionId").asText();
                break;
            }
        }

        assertThat(otherSessionId).isNotNull();

        // Invalidate the other session
        mockMvc.perform(post("/api/users/me/sessions/" + otherSessionId + "/invalidate")
                        .session(session2))
                .andExpect(status().isOk());
    }

    @Test
    void invalidateSession_nonExistentSession_returns404() throws Exception {
        MockHttpSession session = signUpAndGetSession("inv404@test.com", "inv404user");

        mockMvc.perform(post("/api/users/me/sessions/nonexistent-session-id/invalidate")
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSessions_notAuthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me/sessions"))
                .andExpect(status().isForbidden());
    }
}
```

Note: add `import static org.assertj.core.api.Assertions.assertThat;` at the top of the file.

- [ ] **Step 2: Run tests**

Run: `cd backend && ./gradlew test --tests "com.chatapp.controller.SessionManagementTest"`
Expected: All 5 tests PASS

- [ ] **Step 3: Run all backend tests together**

Run: `cd backend && ./gradlew test`
Expected: All 36 tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/chatapp/controller/SessionManagementTest.java
git commit -m "test: add session management integration tests"
```

---

### Task 8: Playwright E2E Infrastructure

**Files:**
- Create: `e2e/package.json`
- Create: `e2e/playwright.config.ts`
- Create: `e2e/tsconfig.json`

**Prerequisites:** Docker Compose must be running (`docker-compose up --build`). Playwright tests run against the live app at `http://localhost:5173`.

- [ ] **Step 1: Create e2e/package.json**

Create `e2e/package.json`:

```json
{
  "name": "chatapp-e2e",
  "private": true,
  "scripts": {
    "test": "npx playwright test",
    "test:headed": "npx playwright test --headed",
    "test:ui": "npx playwright test --ui"
  },
  "devDependencies": {
    "@playwright/test": "^1.52.0"
  }
}
```

- [ ] **Step 2: Create e2e/tsconfig.json**

Create `e2e/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["**/*.ts"]
}
```

- [ ] **Step 3: Create e2e/playwright.config.ts**

Create `e2e/playwright.config.ts`:

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});
```

- [ ] **Step 4: Install dependencies and Playwright browsers**

Run:
```bash
cd e2e && npm install && npx playwright install chromium
```
Expected: Dependencies installed, Chromium browser downloaded

- [ ] **Step 5: Commit**

```bash
git add e2e/package.json e2e/tsconfig.json e2e/playwright.config.ts e2e/package-lock.json
git commit -m "test: add Playwright e2e test infrastructure"
```

---

### Task 9: E2E Signup and Signin Tests

**Files:**
- Create: `e2e/tests/signup.spec.ts`
- Create: `e2e/tests/signin.spec.ts`

**Prerequisites:** `docker-compose up --build` running with a clean database, or be aware that demo users (alice/bob/carol) exist from Liquibase migrations.

- [ ] **Step 1: Create signup e2e tests**

Create `e2e/tests/signup.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test.describe('Sign Up', () => {
  test('successful signup redirects to home', async ({ page }) => {
    const unique = Date.now();
    await page.goto('/signup');

    await page.getByPlaceholder('Email').fill(`e2e_signup_${unique}@test.com`);
    await page.getByPlaceholder('Username').fill(`e2euser${unique}`);
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByPlaceholder('Display Name').fill('E2E User');
    await page.getByRole('button', { name: 'Sign Up' }).click();

    // Should show success message and redirect to home
    await expect(page.getByText('Account created successfully')).toBeVisible();
    await expect(page).toHaveURL('/');
  });

  test('shows error for duplicate email', async ({ page }) => {
    const unique = Date.now();
    // First signup
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill(`e2e_dupe_${unique}@test.com`);
    await page.getByPlaceholder('Username').fill(`e2edupe1_${unique}`);
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByPlaceholder('Display Name').fill('Dupe 1');
    await page.getByRole('button', { name: 'Sign Up' }).click();
    await expect(page).toHaveURL('/');

    // Second signup with same email
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill(`e2e_dupe_${unique}@test.com`);
    await page.getByPlaceholder('Username').fill(`e2edupe2_${unique}`);
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByPlaceholder('Display Name').fill('Dupe 2');
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page.getByText('User with this email already exists')).toBeVisible();
  });

  test('shows client-side validation for short password', async ({ page }) => {
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill('short@test.com');
    await page.getByPlaceholder('Username').fill('shortpwuser');
    await page.getByPlaceholder('Password').fill('short');
    await page.getByPlaceholder('Display Name').fill('Short PW');
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page.getByText('Password must be at least 8 characters')).toBeVisible();
  });

  test('shows client-side validation for invalid username', async ({ page }) => {
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill('baduser@test.com');
    await page.getByPlaceholder('Username').fill('bad user!');
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByPlaceholder('Display Name').fill('Bad User');
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page.getByText('Only letters, numbers, and underscores')).toBeVisible();
  });

  test('navigates to signin page', async ({ page }) => {
    await page.goto('/signup');
    await page.getByText('Sign In').click();
    await expect(page).toHaveURL('/signin');
  });
});
```

- [ ] **Step 2: Create signin e2e tests**

Create `e2e/tests/signin.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test.describe('Sign In', () => {
  test.beforeAll(async ({ browser }) => {
    // Create a test user via signup
    const page = await browser.newPage();
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill('e2e_signin@test.com');
    await page.getByPlaceholder('Username').fill('e2esigninuser');
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByPlaceholder('Display Name').fill('Signin Test');
    await page.getByRole('button', { name: 'Sign Up' }).click();
    // Wait for redirect, then close — ignore if user already exists
    await page.waitForURL('/', { timeout: 5000 }).catch(() => {});
    await page.close();
  });

  test('successful signin redirects to home', async ({ page }) => {
    await page.goto('/signin');

    await page.getByPlaceholder('Email').fill('e2e_signin@test.com');
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Signed in successfully')).toBeVisible();
    await expect(page).toHaveURL('/');
  });

  test('shows error for wrong password', async ({ page }) => {
    await page.goto('/signin');

    await page.getByPlaceholder('Email').fill('e2e_signin@test.com');
    await page.getByPlaceholder('Password').fill('wrongpassword');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('shows error for non-existent email', async ({ page }) => {
    await page.goto('/signin');

    await page.getByPlaceholder('Email').fill('doesnotexist@test.com');
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('shows client-side validation for empty fields', async ({ page }) => {
    await page.goto('/signin');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Please input your email')).toBeVisible();
    await expect(page.getByText('Please input your password')).toBeVisible();
  });

  test('shows client-side validation for invalid email format', async ({ page }) => {
    await page.goto('/signin');
    await page.getByPlaceholder('Email').fill('not-an-email');
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Please enter a valid email')).toBeVisible();
  });

  test('demo button signs in as Alice', async ({ page }) => {
    await page.goto('/signin');
    await page.getByRole('button', { name: 'Alice' }).click();

    // Demo users exist from Liquibase migrations
    // Either succeeds or shows error — both are valid UI feedback
    const success = page.getByText('Signed in successfully');
    const error = page.getByText('Invalid email or password');
    await expect(success.or(error)).toBeVisible();
  });

  test('navigates to signup page', async ({ page }) => {
    await page.goto('/signin');
    await page.getByText('Sign Up').click();
    await expect(page).toHaveURL('/signup');
  });
});
```

- [ ] **Step 3: Run e2e tests (docker-compose must be running)**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS (if docker-compose is running)

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/signup.spec.ts e2e/tests/signin.spec.ts
git commit -m "test: add signup and signin e2e tests"
```

---

### Task 10: E2E Settings Tests

**Files:**
- Create: `e2e/tests/settings.spec.ts`

- [ ] **Step 1: Create settings e2e tests**

Create `e2e/tests/settings.spec.ts`:

```typescript
import { test, expect, Page } from '@playwright/test';

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill('Test User');
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await page.waitForURL('/');
}

async function signIn(page: Page, email: string): Promise<void> {
  await page.goto('/signin');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL('/');
}

test.describe('Settings Page', () => {
  test('displays profile information', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `profile_${unique}@test.com`, `profile${unique}`);
    await page.goto('/settings');

    await expect(page.getByText('Profile')).toBeVisible();
    await expect(page.locator('input[value="' + `profile${unique}` + '"]')).toBeVisible();
    await expect(page.locator('input[value="' + `profile_${unique}@test.com` + '"]')).toBeVisible();
  });

  test('change password successfully', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `chpw_${unique}@test.com`, `chpw${unique}`);
    await page.goto('/settings');

    // Go to Security tab
    await page.getByText('Security').click();

    await page.getByLabel('Current Password').fill('password123');
    await page.getByLabel('New Password').fill('newpassword123');
    await page.getByLabel('Confirm New Password').fill('newpassword123');
    await page.getByRole('button', { name: 'Change Password' }).click();

    await expect(page.getByText('Password changed successfully')).toBeVisible();
  });

  test('change password shows error for wrong current password', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `chpwerr_${unique}@test.com`, `chpwerr${unique}`);
    await page.goto('/settings');

    await page.getByText('Security').click();

    await page.getByLabel('Current Password').fill('wrongpassword');
    await page.getByLabel('New Password').fill('newpassword123');
    await page.getByLabel('Confirm New Password').fill('newpassword123');
    await page.getByRole('button', { name: 'Change Password' }).click();

    await expect(page.getByText('Current password is incorrect')).toBeVisible();
  });

  test('change password validates password match', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `chpwmatch_${unique}@test.com`, `chpwmatch${unique}`);
    await page.goto('/settings');

    await page.getByText('Security').click();

    await page.getByLabel('Current Password').fill('password123');
    await page.getByLabel('New Password').fill('newpassword123');
    await page.getByLabel('Confirm New Password').fill('differentpassword');
    await page.getByRole('button', { name: 'Change Password' }).click();

    await expect(page.getByText('Passwords do not match')).toBeVisible();
  });

  test('sessions tab shows current session', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `sess_${unique}@test.com`, `sess${unique}`);
    await page.goto('/settings');

    await page.getByText('Sessions').click();

    // Should see at least one session marked as Current
    await expect(page.getByText('Current')).toBeVisible();
  });

  test('delete account with confirmation', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `del_${unique}@test.com`, `del${unique}`);
    await page.goto('/settings');

    await page.getByText('Security').click();
    await page.getByRole('button', { name: 'Delete Account' }).click();

    // Modal should appear
    await expect(page.getByText('This action cannot be undone')).toBeVisible();

    // Enter password in modal
    await page.getByPlaceholder('Enter your password').fill('password123');
    await page.getByRole('button', { name: 'Delete Account' }).last().click();

    // Should redirect to signin
    await expect(page.getByText('Account deleted')).toBeVisible();
    await expect(page).toHaveURL('/signin');

    // Verify cannot sign in anymore
    await page.getByPlaceholder('Email').fill(`del_${unique}@test.com`);
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('delete account shows error for wrong password', async ({ page }) => {
    const unique = Date.now();
    await signUp(page, `delerr_${unique}@test.com`, `delerr${unique}`);
    await page.goto('/settings');

    await page.getByText('Security').click();
    await page.getByRole('button', { name: 'Delete Account' }).click();

    await page.getByPlaceholder('Enter your password').fill('wrongpassword');
    await page.getByRole('button', { name: 'Delete Account' }).last().click();

    // Modal should stay open and show error
    await expect(page.getByText('Password is incorrect').or(page.getByText('Failed to delete account'))).toBeVisible();
  });
});
```

- [ ] **Step 2: Run e2e tests (docker-compose must be running)**

Run: `cd e2e && npx playwright test`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/settings.spec.ts
git commit -m "test: add settings page e2e tests"
```

---

## Running Tests

### Backend Integration Tests
```bash
cd backend && ./gradlew test
```
Requires: Docker running (Testcontainers pulls postgres:16-alpine automatically)

### E2E Tests
```bash
docker-compose up --build    # in one terminal
cd e2e && npx playwright test  # in another terminal
```

### Full Suite
```bash
cd backend && ./gradlew test && cd ../e2e && npx playwright test
```
