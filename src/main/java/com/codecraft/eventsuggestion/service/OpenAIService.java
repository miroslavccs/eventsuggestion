package com.codecraft.eventsuggestion.service;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;
    private final boolean apiKeyMissing;

    public OpenAIService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.model}") String model,
            @Value("${openai.max-tokens}") int maxTokens,
            ObjectMapper objectMapper) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
        this.apiKeyMissing = apiKey == null || apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public record SuggestionData(
            String title,
            String description,
            String location,
            String estimatedCost,
            String suggestedDate,
            String reasonForSuggestion
    ) {}

    /**
     * Calls OpenAI to generate personalized suggestions for a customer.
     * Includes past feedback history so the model can learn preferences.
     */
    public List<SuggestionData> generateSuggestions(
            Customer customer,
            CustomerPreferences prefs,
            List<Suggestion> recentFeedback,
            SuggestionCategory category,
            int count) {

        if (isApiKeyMissing()) {
            log.warn("OpenAI API key not configured — returning empty suggestions");
            return List.of();
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(customer, prefs, recentFeedback, category, count);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", maxTokens,
                    "response_format", Map.of("type", "json_object")
            );

            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseSuggestions(responseJson);
        } catch (Exception e) {
            log.error("OpenAI API call failed for customer {}: {}", customer.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Asks OpenAI to analyse recent feedback and produce a brief learned-profile summary
     * that will be stored in CustomerPreferences and fed back into future prompts.
     */
    public String generateLearnedProfile(Customer customer, CustomerPreferences prefs, List<Suggestion> feedback) {
        if (isApiKeyMissing() || feedback.isEmpty()) return null;

        String prompt = buildLearningPrompt(customer, feedback);
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a data analyst summarising customer behavioural patterns for a personalised recommendation engine."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 500
            );

            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Failed to generate learned profile for customer {}: {}", customer.getId(), e.getMessage());
            return null;
        }
    }

    private boolean isApiKeyMissing() {
        return apiKeyMissing;
    }

    private String buildSystemPrompt() {
        return """
                You are a personal life enrichment advisor. Your role is to suggest activities,
                events, and experiences that help people live more fulfilling, balanced lives.
                Always respond with valid JSON only. Be specific, practical, and relevant to the
                customer's location and interests. Vary suggestions based on past feedback patterns.
                """;
    }

    private String buildUserPrompt(Customer customer, CustomerPreferences prefs,
                                   List<Suggestion> feedback, SuggestionCategory category, int count) {
        StringBuilder sb = new StringBuilder();

        sb.append("Generate exactly ").append(count).append(" personalized suggestions.\n\n");

        sb.append("CUSTOMER PROFILE:\n");
        sb.append("- Name: ").append(customer.getFirstName()).append(" ").append(customer.getLastName()).append("\n");
        if (customer.getAge() != null) sb.append("- Age: ").append(customer.getAge()).append("\n");
        if (customer.getGender() != null) sb.append("- Gender: ").append(customer.getGender()).append("\n");
        if (customer.getAddress() != null) {
            sb.append("- Location: ").append(customer.getAddress().getCity())
              .append(", ").append(customer.getAddress().getCountry()).append("\n");
        }
        if (customer.getEducation() != null) sb.append("- Education: ").append(customer.getEducation()).append("\n");
        if (customer.getCurrentEmployment() != null) sb.append("- Employment: ").append(customer.getCurrentEmployment()).append("\n");

        if (prefs != null) {
            sb.append("\nINTERESTS & PREFERENCES:\n");
            if (!prefs.getSports().isEmpty()) sb.append("- Sports: ").append(String.join(", ", prefs.getSports())).append("\n");
            if (!prefs.getHobbies().isEmpty()) sb.append("- Hobbies: ").append(String.join(", ", prefs.getHobbies())).append("\n");
            if (!prefs.getInterests().isEmpty()) sb.append("- Other Interests: ").append(String.join(", ", prefs.getInterests())).append("\n");
            sb.append("- Likes Traveling: ").append(prefs.isLikesTraveling()).append("\n");
            sb.append("- Likes Nightlife: ").append(prefs.isLikesNightlife()).append("\n");
            if (prefs.getAdditionalNotes() != null) sb.append("- Notes: ").append(prefs.getAdditionalNotes()).append("\n");

            if (prefs.getLearnedProfile() != null) {
                sb.append("\nLEARNED INSIGHTS FROM PAST BEHAVIOUR:\n").append(prefs.getLearnedProfile()).append("\n");
            }
        }

        if (!feedback.isEmpty()) {
            sb.append("\nPAST SUGGESTION HISTORY:\n");
            for (Suggestion s : feedback) {
                sb.append("- [").append(s.getStatus()).append("] \"").append(s.getTitle()).append("\"");
                if (s.getFeedbackComment() != null) sb.append(" — comment: \"").append(s.getFeedbackComment()).append("\"");
                sb.append("\n");
            }
        }

        sb.append("\nCATEGORY: ").append(category).append("\n");
        sb.append(switch (category) {
            case DAILY -> "Focus on nearby events, concerts, plays, sport events within the next 1-3 days.";
            case WEEKEND -> "Focus on weekend getaways and area events requiring more time (upcoming weekend).";
            case MONTHLY -> "Focus on longer trips, seasonal activities, travel destinations (upcoming month).";
        }).append("\n");

        sb.append("\nToday's date: ").append(LocalDate.now()).append("\n");

        sb.append("""

                Respond with a JSON object containing a "suggestions" array:
                {
                  "suggestions": [
                    {
                      "title": "...",
                      "description": "2-3 sentence description",
                      "location": "city, country or venue name",
                      "estimatedCost": "e.g. Free, $20-50, $200+",
                      "suggestedDate": "YYYY-MM-DD",
                      "reasonForSuggestion": "one sentence why this fits this customer"
                    }
                  ]
                }
                """);

        return sb.toString();
    }

    private String buildLearningPrompt(Customer customer, List<Suggestion> feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyse this customer's suggestion feedback and write 3-5 concise bullet points summarising their preferences, dislikes, and behavioural patterns to guide future recommendations.\n\n");
        sb.append("Customer: ").append(customer.getFirstName()).append(", age ").append(customer.getAge()).append("\n\n");
        sb.append("Feedback history:\n");
        for (Suggestion s : feedback) {
            sb.append("- [").append(s.getStatus()).append("] ").append(s.getCategory())
              .append(": \"").append(s.getTitle()).append("\"");
            if (s.getFeedbackComment() != null) sb.append(" — \"").append(s.getFeedbackComment()).append("\"");
            sb.append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<SuggestionData> parseSuggestions(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        JsonNode parsed = objectMapper.readTree(content);
        return objectMapper.convertValue(
                parsed.get("suggestions"),
                new TypeReference<List<SuggestionData>>() {}
        );
    }
}
