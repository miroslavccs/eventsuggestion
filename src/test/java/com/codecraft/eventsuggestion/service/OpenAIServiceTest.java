package com.codecraft.eventsuggestion.service;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.SuggestionContent;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setEmail("jane@example.com");
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setAge(30);
        return c;
    }

    private Suggestion feedbackSuggestion() {
        SuggestionContent content = new SuggestionContent();
        content.setCategory(SuggestionCategory.DAILY);
        content.setTitle("Test");
        Suggestion s = new Suggestion();
        s.setContent(content);
        return s;
    }

    private String baseUrlOf(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private HttpServer startServerReturning(int status, String body) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        s.createContext("/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        s.start();
        return s;
    }

    private String chatCompletionResponse(String content) {
        Map<String, Object> root = Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content))));
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    // ─── apiKeyMissing / empty-feedback guards ───────────────────────────────

    @Test
    void generateSuggestions_blankApiKey_returnsEmptyListWithoutHttpCall() {
        OpenAIService service = new OpenAIService("", "http://localhost:1", "gpt-4o", 2000, OBJECT_MAPPER);

        List<OpenAIService.SuggestionData> result =
                service.generateSuggestions(customer(), null, List.of(), SuggestionCategory.DAILY, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void generateLearnedProfile_blankApiKey_returnsNull() {
        OpenAIService service = new OpenAIService("", "http://localhost:1", "gpt-4o", 2000, OBJECT_MAPPER);

        String result = service.generateLearnedProfile(customer(), null, List.of(feedbackSuggestion()));

        assertThat(result).isNull();
    }

    @Test
    void generateLearnedProfile_emptyFeedback_returnsNullWithoutHttpCall() {
        OpenAIService service = new OpenAIService("sk-test", "http://localhost:1", "gpt-4o", 2000, OBJECT_MAPPER);

        String result = service.generateLearnedProfile(customer(), null, List.of());

        assertThat(result).isNull();
    }

    // ─── success paths (embedded HTTP server) ────────────────────────────────

    @Test
    void generateSuggestions_success_parsesSuggestionDataFromResponse() throws IOException {
        String content = "{\"suggestions\":[{\"title\":\"Jazz night\",\"description\":\"Live jazz\","
                + "\"location\":\"London\",\"estimatedCost\":\"$20\",\"suggestedDate\":\"2027-01-01\","
                + "\"reasonForSuggestion\":\"Matches interests\"}]}";
        server = startServerReturning(200, chatCompletionResponse(content));
        OpenAIService service = new OpenAIService("sk-test", baseUrlOf(server), "gpt-4o", 2000, OBJECT_MAPPER);

        List<OpenAIService.SuggestionData> result =
                service.generateSuggestions(customer(), null, List.of(), SuggestionCategory.DAILY, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Jazz night");
        assertThat(result.get(0).suggestedDate()).isEqualTo("2027-01-01");
    }

    @Test
    void generateLearnedProfile_success_returnsMessageContent() throws IOException {
        server = startServerReturning(200, chatCompletionResponse("Likes jazz, dislikes opera"));
        OpenAIService service = new OpenAIService("sk-test", baseUrlOf(server), "gpt-4o", 2000, OBJECT_MAPPER);

        String result = service.generateLearnedProfile(
                customer(), null, List.of(feedbackSuggestion()));

        assertThat(result).isEqualTo("Likes jazz, dislikes opera");
    }

    // ─── error paths ──────────────────────────────────────────────────────────

    @Test
    void generateSuggestions_malformedContentJson_returnsEmptyList() throws IOException {
        server = startServerReturning(200, chatCompletionResponse("not valid json"));
        OpenAIService service = new OpenAIService("sk-test", baseUrlOf(server), "gpt-4o", 2000, OBJECT_MAPPER);

        List<OpenAIService.SuggestionData> result =
                service.generateSuggestions(customer(), null, List.of(), SuggestionCategory.DAILY, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void generateSuggestions_serverError_returnsEmptyList() throws IOException {
        server = startServerReturning(500, "{\"error\":\"boom\"}");
        OpenAIService service = new OpenAIService("sk-test", baseUrlOf(server), "gpt-4o", 2000, OBJECT_MAPPER);

        List<OpenAIService.SuggestionData> result =
                service.generateSuggestions(customer(), null, List.of(), SuggestionCategory.DAILY, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void generateLearnedProfile_serverError_returnsNull() throws IOException {
        server = startServerReturning(500, "{\"error\":\"boom\"}");
        OpenAIService service = new OpenAIService("sk-test", baseUrlOf(server), "gpt-4o", 2000, OBJECT_MAPPER);

        String result = service.generateLearnedProfile(
                customer(), null, List.of(feedbackSuggestion()));

        assertThat(result).isNull();
    }
}
