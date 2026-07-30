package com.codecraft.eventsuggestion;

import com.codecraft.eventsuggestion.dto.CustomerProfileDto;
import com.codecraft.eventsuggestion.dto.LoginRequest;
import com.codecraft.eventsuggestion.dto.LoginResponse;
import com.codecraft.eventsuggestion.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private RegisterRequest registerRequest(String email) {
        return new RegisterRequest(
                email, "secret123", "Jane", "Doe", null, 30,
                null, "MSc", "Engineer", List.of(), List.of(), List.of(), true, false, null);
    }

    @Test
    void protectedEndpoint_withoutToken_isRejected() {
        client.get().uri("/api/customers/me")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void protectedEndpoint_withGarbageToken_isRejected() {
        client.get().uri("/api/customers/me")
                .header("Authorization", "Bearer not-a-real-jwt")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void registerThenFetchProfile_withIssuedToken_succeeds() {
        String email = "integration-" + System.nanoTime() + "@example.com";

        LoginResponse registerBody = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerRequest(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(registerBody.token()).isNotBlank();

        CustomerProfileDto profile = client.get().uri("/api/customers/me")
                .header("Authorization", "Bearer " + registerBody.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(CustomerProfileDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(profile.email()).isEqualTo(email);
        assertThat(profile.firstName()).isEqualTo("Jane");
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerRequest(email))
                .exchange()
                .expectStatus().isCreated();

        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerRequest(email))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        String email = "login-" + System.nanoTime() + "@example.com";
        client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerRequest(email))
                .exchange()
                .expectStatus().isCreated();

        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void register_blankEmail_returnsBadRequestWithFieldErrors() {
        RegisterRequest invalid = new RegisterRequest(
                "", "secret123", "Jane", "Doe", null, 30,
                null, null, null, List.of(), List.of(), List.of(), false, false, null);

        Map body = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).containsKey("email");
    }
}
