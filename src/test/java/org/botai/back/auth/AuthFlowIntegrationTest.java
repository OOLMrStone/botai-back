package org.botai.back.auth;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end auth pipeline test against a real Postgres (Testcontainers):
 * CSRF bootstrap, registration, login (session cookie), authenticated request,
 * logout, and the relevant failure paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @Test
    @Order(1)
    void fullRegisterLoginMeLogoutFlow() {
        // 1. CSRF bootstrap. Like the real SPA, we read the raw token from the
        // XSRF-TOKEN cookie and echo it back in the X-XSRF-TOKEN header.
        ResponseEntity<String> csrfResponse = rest.getForEntity("/api/auth/csrf", String.class);
        assertThat(csrfResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csrfCookie = extractCookie(csrfResponse.getHeaders(), "XSRF-TOKEN");
        assertThat(csrfCookie).isNotBlank();
        assertThat(objectMapper.readTree(csrfResponse.getBody()).get("token").asString()).isNotBlank();

        // 2. Register
        ResponseEntity<String> registerResponse = postJson("/api/auth/register",
                """
                {"email": "%s", "password": "%s", "displayName": "Alice"}
                """.formatted(EMAIL, PASSWORD),
                csrfCookie, csrfCookie, null);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode registered = objectMapper.readTree(registerResponse.getBody());
        assertThat(registered.get("email").asString()).isEqualTo(EMAIL);
        assertThat(registered.get("authProvider").asString()).isEqualTo("LOCAL");
        // password (or its hash) must never leak into the response
        assertThat(registerResponse.getBody()).doesNotContain(PASSWORD).doesNotContain("password");

        // 3. Login: establishes the server-side session, sets the SESSION cookie
        ResponseEntity<String> loginResponse = postJson("/api/auth/login",
                """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD),
                csrfCookie, csrfCookie, null);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String sessionCookie = extractCookie(loginResponse.getHeaders(), "SESSION");
        assertThat(sessionCookie).isNotBlank();

        // 4. Authenticated request with the session cookie
        ResponseEntity<String> meResponse = getWithCookies("/api/auth/me",
                "SESSION=" + sessionCookie);
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(meResponse.getBody()).get("email").asString()).isEqualTo(EMAIL);

        // 5. A forged/garbage session cookie is rejected
        ResponseEntity<String> forgedResponse = getWithCookies("/api/auth/me",
                "SESSION=" + "Zm9yZ2VkLXNlc3Npb24taWQtMTIzNDU2Nzg5MGFiY2RlZg==");
        assertThat(forgedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 6. Logout invalidates the session server-side
        ResponseEntity<String> logoutResponse = postJson("/api/auth/logout", null,
                csrfCookie, csrfCookie, sessionCookie);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> afterLogout = getWithCookies("/api/auth/me",
                "SESSION=" + sessionCookie);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void rejectsDuplicateEmailWrongPasswordAndMissingCsrf() {
        ResponseEntity<String> csrfResponse = rest.getForEntity("/api/auth/csrf", String.class);
        String csrfCookie = extractCookie(csrfResponse.getHeaders(), "XSRF-TOKEN");

        // duplicate email (registered in the previous test) -> 409
        ResponseEntity<String> duplicate = postJson("/api/auth/register",
                """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL.toUpperCase(), PASSWORD),
                csrfCookie, csrfCookie, null);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // wrong password -> 401 with a generic message (no account enumeration)
        ResponseEntity<String> badLogin = postJson("/api/auth/login",
                """
                {"email": "%s", "password": "wrong-password"}
                """.formatted(EMAIL),
                csrfCookie, csrfCookie, null);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(badLogin.getBody()).contains("Invalid email or password");

        // unknown email must produce the exact same response
        ResponseEntity<String> unknownLogin = postJson("/api/auth/login",
                """
                {"email": "nobody@example.com", "password": "wrong-password"}
                """,
                csrfCookie, csrfCookie, null);
        assertThat(unknownLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownLogin.getBody()).contains("Invalid email or password");

        // missing CSRF token -> rejected before the endpoint runs, even with
        // valid credentials (401 because the caller is anonymous; authenticated
        // callers would get 403)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> noCsrf = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, PASSWORD), headers),
                String.class);
        assertThat(noCsrf.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(extractCookie(noCsrf.getHeaders(), "SESSION")).isNull();

        // weak password -> 400 (bean validation)
        ResponseEntity<String> weakPassword = postJson("/api/auth/register",
                """
                {"email": "bob@example.com", "password": "short"}
                """,
                csrfCookie, csrfCookie, null);
        assertThat(weakPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> postJson(String path, String body, String csrfCookie,
                                            String csrfToken, String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-XSRF-TOKEN", csrfToken);
        String cookies = "XSRF-TOKEN=" + csrfCookie;
        if (sessionCookie != null) {
            cookies += "; SESSION=" + sessionCookie;
        }
        headers.set(HttpHeaders.COOKIE, cookies);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> getWithCookies(String path, String cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookies);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String extractCookie(HttpHeaders headers, String name) {
        List<String> setCookies = headers.getOrEmpty(HttpHeaders.SET_COOKIE);
        return setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .findFirst()
                .orElse(null);
    }
}
