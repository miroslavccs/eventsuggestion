package com.codecraft.eventsuggestion;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.SuggestionContent;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;
import com.codecraft.eventsuggestion.dto.FeedbackRequest;
import com.codecraft.eventsuggestion.dto.LoginResponse;
import com.codecraft.eventsuggestion.dto.PlanRequest;
import com.codecraft.eventsuggestion.dto.RatingRequest;
import com.codecraft.eventsuggestion.dto.RegisterRequest;
import com.codecraft.eventsuggestion.dto.SnoozeRequest;
import com.codecraft.eventsuggestion.dto.SuggestionDto;
import com.codecraft.eventsuggestion.repository.CustomerRepository;
import com.codecraft.eventsuggestion.repository.SuggestionContentRepository;
import com.codecraft.eventsuggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuggestionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private SuggestionContentRepository suggestionContentRepository;

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

    private record RegisteredUser(String token, Customer customer) {}

    private RegisteredUser registerCustomer(String email) {
        LoginResponse body = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerRequest(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        Customer customer = customerRepository.findByEmail(email).orElseThrow();
        return new RegisteredUser(body.token(), customer);
    }

    private Suggestion seedSuggestion(Customer customer, SuggestionCategory category, String title) {
        SuggestionContent content = new SuggestionContent();
        content.setCategory(category);
        content.setTitle(title);
        content = suggestionContentRepository.save(content);

        Suggestion s = new Suggestion();
        s.setCustomer(customer);
        s.setContent(content);
        s.setStatus(SuggestionStatus.PENDING);
        s.setNotificationRead(false);
        return suggestionRepository.save(s);
    }

    private Suggestion seedAcceptedSuggestion(Customer customer, LocalDate suggestedDate) {
        Suggestion s = seedSuggestion(customer, SuggestionCategory.DAILY, "Accepted pick");
        s.setStatus(SuggestionStatus.ACCEPTED);
        s.setSuggestedDate(suggestedDate);
        return suggestionRepository.save(s);
    }

    private Suggestion seedWishlistSuggestion(Customer customer) {
        Suggestion s = seedSuggestion(customer, SuggestionCategory.MONTHLY, "Wishlist pick");
        s.setStatus(SuggestionStatus.WISHLIST);
        return suggestionRepository.save(s);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    @Test
    void suggestions_withoutToken_isRejected() {
        client.get().uri("/api/suggestions")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void getSuggestions_withNoneYet_returnsEmptyList() {
        RegisteredUser user = registerCustomer(uniqueEmail("suggestions-empty"));

        client.get().uri("/api/suggestions")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> assertThat(body).isEmpty());
    }

    @Test
    void generate_withNoOpenAiKeyConfigured_returnsEmptyListEndToEnd() {
        RegisteredUser user = registerCustomer(uniqueEmail("generate"));

        client.post().uri("/api/suggestions/generate?category=DAILY")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> assertThat(body).isEmpty());
    }

    @Test
    void getSuggestions_filtersByCategory() {
        RegisteredUser user = registerCustomer(uniqueEmail("filter"));
        seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Daily pick");
        seedSuggestion(user.customer(), SuggestionCategory.WEEKEND, "Weekend pick");

        client.get().uri("/api/suggestions")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> assertThat(body).hasSize(2));

        client.get().uri("/api/suggestions?category=DAILY")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> {
                    assertThat(body).hasSize(1);
                    assertThat(body[0].category()).isEqualTo(SuggestionCategory.DAILY);
                });
    }

    @Test
    void notifications_reflectsOnlyUnreadSuggestions() {
        RegisteredUser user = registerCustomer(uniqueEmail("notifications"));
        Suggestion unread = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Unread pick");
        Suggestion read = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Read pick");
        read.setNotificationRead(true);
        suggestionRepository.save(read);

        client.get().uri("/api/suggestions/notifications")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> {
                    assertThat(body).hasSize(1);
                    assertThat(body[0].id()).isEqualTo(unread.getId());
                });
    }

    @Test
    void feedback_accepted_updatesStatusAndClearsNotification() {
        RegisteredUser user = registerCustomer(uniqueEmail("feedback-accept"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Accept me");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/feedback")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FeedbackRequest(SuggestionStatus.ACCEPTED, "Looking forward to it"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto.class)
                .value(dto -> {
                    assertThat(dto.status()).isEqualTo(SuggestionStatus.ACCEPTED);
                    assertThat(dto.feedbackComment()).isEqualTo("Looking forward to it");
                    assertThat(dto.respondedAt()).isNotNull();
                    assertThat(dto.notificationRead()).isTrue();
                });
    }

    @Test
    void feedback_statusPending_returnsBadRequestProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("feedback-pending"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Stay pending");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/feedback")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FeedbackRequest(SuggestionStatus.PENDING, null))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("detail")).isEqualTo("Cannot set status back to PENDING"));
    }

    @Test
    void feedback_missingStatus_returnsValidationProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("feedback-missing-status"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Needs a status");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/feedback")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("comment", "no status provided"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsKey("errors");
                    assertThat((Map) body.get("errors")).containsKey("status");
                });
    }

    @Test
    void feedback_unknownSuggestionId_returnsNotFound() {
        RegisteredUser user = registerCustomer(uniqueEmail("feedback-unknown"));

        client.put().uri("/api/suggestions/999999/feedback")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FeedbackRequest(SuggestionStatus.ACCEPTED, null))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void feedback_onAnotherCustomersSuggestion_returnsNotFound() {
        RegisteredUser owner = registerCustomer(uniqueEmail("feedback-owner"));
        RegisteredUser stranger = registerCustomer(uniqueEmail("feedback-stranger"));
        Suggestion suggestion = seedSuggestion(owner.customer(), SuggestionCategory.DAILY, "Not yours");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/feedback")
                .header("Authorization", "Bearer " + stranger.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FeedbackRequest(SuggestionStatus.ACCEPTED, null))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void markRead_marksNotificationReadAndRemovesFromFeed() {
        RegisteredUser user = registerCustomer(uniqueEmail("mark-read"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Dismiss me");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/read")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/suggestions/notifications")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> assertThat(body).isEmpty());
    }

    @Test
    void snooze_pendingSuggestion_hidesFromNotificationsUntilDate() {
        RegisteredUser user = registerCustomer(uniqueEmail("snooze-happy"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Snooze me");
        LocalDate until = LocalDate.now().plusDays(3);

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/snooze")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SnoozeRequest(until))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto.class)
                .value(dto -> assertThat(dto.snoozedUntil()).isEqualTo(until));

        client.get().uri("/api/suggestions/notifications")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> assertThat(body).isEmpty());
    }

    @Test
    void snooze_pastDate_returnsValidationProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("snooze-past-date"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Bad date");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/snooze")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SnoozeRequest(LocalDate.now().minusDays(1)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsKey("errors"));
    }

    @Test
    void snooze_nonPendingSuggestion_returnsBadRequestProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("snooze-non-pending"));
        Suggestion suggestion = seedAcceptedSuggestion(user.customer(), LocalDate.now().minusDays(1));

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/snooze")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SnoozeRequest(LocalDate.now().plusDays(1)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("detail")).isEqualTo("Only pending suggestions can be snoozed"));
    }

    @Test
    void snooze_unknownSuggestionId_returnsNotFound() {
        RegisteredUser user = registerCustomer(uniqueEmail("snooze-unknown"));

        client.put().uri("/api/suggestions/999999/snooze")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SnoozeRequest(LocalDate.now().plusDays(1)))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void notifications_excludesFutureSnoozed_includesElapsedSnoozed() {
        RegisteredUser user = registerCustomer(uniqueEmail("snooze-notifications"));
        Suggestion stillSnoozed = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Still snoozed");
        stillSnoozed.setSnoozedUntil(LocalDate.now().plusDays(3));
        suggestionRepository.save(stillSnoozed);

        Suggestion elapsedSnooze = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Snooze elapsed");
        elapsedSnooze.setSnoozedUntil(LocalDate.now().minusDays(1));
        suggestionRepository.save(elapsedSnooze);

        client.get().uri("/api/suggestions/notifications")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto[].class)
                .value(body -> {
                    assertThat(body).hasSize(1);
                    assertThat(body[0].id()).isEqualTo(elapsedSnooze.getId());
                });
    }

    @Test
    void rating_acceptedAndPastDated_succeeds() {
        RegisteredUser user = registerCustomer(uniqueEmail("rating-happy"));
        Suggestion suggestion = seedAcceptedSuggestion(user.customer(), LocalDate.now().minusDays(1));

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/rating")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RatingRequest(4))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto.class)
                .value(dto -> assertThat(dto.rating()).isEqualTo(4));
    }

    @Test
    void rating_pendingSuggestion_returnsBadRequestProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("rating-pending"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Not accepted");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/rating")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RatingRequest(4))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("detail")).isEqualTo("Can only rate an accepted suggestion"));
    }

    @Test
    void rating_dateNotYetPassed_returnsBadRequestProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("rating-future"));
        Suggestion suggestion = seedAcceptedSuggestion(user.customer(), LocalDate.now().plusDays(1));

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/rating")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RatingRequest(4))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("detail")).isEqualTo("Cannot rate a suggestion before its date"));
    }

    @Test
    void rating_outOfRange_returnsValidationProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("rating-out-of-range"));
        Suggestion suggestion = seedAcceptedSuggestion(user.customer(), LocalDate.now().minusDays(1));

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/rating")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RatingRequest(6))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsKey("errors"));
    }

    @Test
    void rating_unknownSuggestionId_returnsNotFound() {
        RegisteredUser user = registerCustomer(uniqueEmail("rating-unknown"));

        client.put().uri("/api/suggestions/999999/rating")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RatingRequest(4))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void plan_wishlistWithTargetDate_promotesAndSetsDate() {
        RegisteredUser user = registerCustomer(uniqueEmail("plan-with-date"));
        Suggestion suggestion = seedWishlistSuggestion(user.customer());
        LocalDate target = LocalDate.now().plusMonths(1);

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/plan")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRequest(target))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto.class)
                .value(dto -> {
                    assertThat(dto.status()).isEqualTo(SuggestionStatus.ACCEPTED);
                    assertThat(dto.suggestedDate()).isEqualTo(target);
                });
    }

    @Test
    void plan_wishlistWithoutTargetDate_promotesOnly() {
        RegisteredUser user = registerCustomer(uniqueEmail("plan-no-date"));
        Suggestion suggestion = seedWishlistSuggestion(user.customer());

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/plan")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRequest(null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SuggestionDto.class)
                .value(dto -> assertThat(dto.status()).isEqualTo(SuggestionStatus.ACCEPTED));
    }

    @Test
    void plan_nonWishlistSuggestion_returnsBadRequestProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("plan-non-wishlist"));
        Suggestion suggestion = seedSuggestion(user.customer(), SuggestionCategory.DAILY, "Still pending");

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/plan")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRequest(null))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("detail")).isEqualTo("Only wishlist suggestions can be planned"));
    }

    @Test
    void plan_pastTargetDate_returnsValidationProblemDetail() {
        RegisteredUser user = registerCustomer(uniqueEmail("plan-past-date"));
        Suggestion suggestion = seedWishlistSuggestion(user.customer());

        client.put().uri("/api/suggestions/" + suggestion.getId() + "/plan")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRequest(LocalDate.now().minusDays(1)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsKey("errors"));
    }

    @Test
    void plan_unknownSuggestionId_returnsNotFound() {
        RegisteredUser user = registerCustomer(uniqueEmail("plan-unknown"));

        client.put().uri("/api/suggestions/999999/plan")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlanRequest(null))
                .exchange()
                .expectStatus().isNotFound();
    }
}
