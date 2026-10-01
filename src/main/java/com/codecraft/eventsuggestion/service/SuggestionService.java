package com.codecraft.eventsuggestion.service;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;
import com.codecraft.eventsuggestion.dto.FeedbackRequest;
import com.codecraft.eventsuggestion.dto.SuggestionDto;
import com.codecraft.eventsuggestion.repository.CustomerPreferencesRepository;
import com.codecraft.eventsuggestion.repository.CustomerRepository;
import com.codecraft.eventsuggestion.repository.SuggestionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);
    private static final int SUGGESTIONS_PER_RUN = 3;
    private static final int FEEDBACK_HISTORY_LIMIT = 30;
    private static final int LEARNING_UPDATE_INTERVAL = 5;
    private static final String DEFAULT_REASON = "Personalized pick based on your profile and preferences";

    private final SuggestionRepository suggestionRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPreferencesRepository preferencesRepository;
    private final OpenAIService openAIService;

    public SuggestionService(SuggestionRepository suggestionRepository,
                             CustomerRepository customerRepository,
                             CustomerPreferencesRepository preferencesRepository,
                             OpenAIService openAIService) {
        this.suggestionRepository = suggestionRepository;
        this.customerRepository = customerRepository;
        this.preferencesRepository = preferencesRepository;
        this.openAIService = openAIService;
    }

    // ─── Scheduled generation ────────────────────────────────────────────────

    /** Daily suggestions every morning at 8:00 */
    @Scheduled(cron = "0 0 8 * * *")
    public void generateDailySuggestions() {
        log.info("Generating DAILY suggestions for all customers");
        generateForAllCustomers(SuggestionCategory.DAILY);
    }

    /** Weekend suggestions every Friday at 09:00 */
    @Scheduled(cron = "0 0 9 * * FRI")
    public void generateWeekendSuggestions() {
        log.info("Generating WEEKEND suggestions for all customers");
        generateForAllCustomers(SuggestionCategory.WEEKEND);
    }

    /** Monthly suggestions on the 1st of each month at 10:00 */
    @Scheduled(cron = "0 0 10 1 * *")
    public void generateMonthlySuggestions() {
        log.info("Generating MONTHLY suggestions for all customers");
        generateForAllCustomers(SuggestionCategory.MONTHLY);
    }

    private void generateForAllCustomers(SuggestionCategory category) {
        customerRepository.findAll().forEach(customer -> {
            try {
                if (isPaused(customer, category)) return;
                generateForCustomer(customer, category);
            } catch (Exception e) {
                log.error("Failed generating {} suggestions for customer {}", category, customer.getId(), e);
            }
        });
    }

    private boolean isPaused(Customer customer, SuggestionCategory category) {
        return preferencesRepository.findByCustomer(customer)
                .map(p -> p.isVacationMode() || p.getPausedCategories().contains(category))
                .orElse(false);
    }

    // ─── On-demand generation (e.g., from controller or after registration) ──

    @Transactional
    public List<SuggestionDto> generateForCustomer(Customer customer, SuggestionCategory category) {
        CustomerPreferences prefs = preferencesRepository.findByCustomer(customer).orElse(null);
        List<Suggestion> recentFeedback = suggestionRepository.findRecentFeedback(
                customer.getId(), PageRequest.of(0, FEEDBACK_HISTORY_LIMIT));

        List<OpenAIService.SuggestionData> aiData =
                openAIService.generateSuggestions(customer, prefs, recentFeedback, category, SUGGESTIONS_PER_RUN);

        List<Suggestion> suggestions = aiData.stream()
                .map(data -> buildSuggestion(customer, category, data))
                .toList();

        return suggestionRepository.saveAll(suggestions).stream()
                .map(SuggestionDto::from)
                .toList();
    }

    // ─── Customer-facing operations ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SuggestionDto> getSuggestions(Long customerId, SuggestionCategory category) {
        List<Suggestion> suggestions = category != null
                ? suggestionRepository.findByCustomerIdAndCategoryOrderByCreatedAtDesc(customerId, category)
                : suggestionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);

        return suggestions.stream().map(SuggestionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SuggestionDto> getNotifications(Long customerId) {
        return suggestionRepository
                .findActiveNotifications(customerId)
                .stream()
                .map(SuggestionDto::from)
                .toList();
    }

    @Transactional
    public SuggestionDto provideFeedback(Long suggestionId, Long customerId, FeedbackRequest req) {
        if (req.status() == SuggestionStatus.PENDING) {
            throw new IllegalArgumentException("Cannot set status back to PENDING");
        }

        Suggestion suggestion = suggestionRepository.findByIdAndCustomerId(suggestionId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found: " + suggestionId));

        suggestion.setStatus(req.status());
        suggestion.setFeedbackComment(req.comment());
        suggestion.setRespondedAt(LocalDateTime.now());
        suggestion.setNotificationRead(true);

        Suggestion saved = suggestionRepository.save(suggestion);

        // Periodically refresh the customer's learned profile
        long feedbackCount = suggestionRepository.countByCustomerIdAndStatusNot(customerId, SuggestionStatus.PENDING);
        if (feedbackCount % LEARNING_UPDATE_INTERVAL == 0) {
            refreshLearnedProfile(customerId);
        }

        return SuggestionDto.from(saved);
    }

    @Transactional
    public void markNotificationRead(Long suggestionId, Long customerId) {
        suggestionRepository.findByIdAndCustomerId(suggestionId, customerId).ifPresent(s -> {
            s.setNotificationRead(true);
            suggestionRepository.save(s);
        });
    }

    @Transactional
    public SuggestionDto snoozeSuggestion(Long suggestionId, Long customerId, LocalDate until) {
        Suggestion suggestion = suggestionRepository.findByIdAndCustomerId(suggestionId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found: " + suggestionId));

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new IllegalArgumentException("Only pending suggestions can be snoozed");
        }

        suggestion.setSnoozedUntil(until);
        return SuggestionDto.from(suggestionRepository.save(suggestion));
    }

    @Transactional
    public SuggestionDto rateSuggestion(Long suggestionId, Long customerId, Integer rating) {
        Suggestion suggestion = suggestionRepository.findByIdAndCustomerId(suggestionId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found: " + suggestionId));

        if (suggestion.getStatus() != SuggestionStatus.ACCEPTED) {
            throw new IllegalArgumentException("Can only rate an accepted suggestion");
        }
        if (suggestion.getSuggestedDate() != null && suggestion.getSuggestedDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot rate a suggestion before its date");
        }

        suggestion.setRating(rating);
        return SuggestionDto.from(suggestionRepository.save(suggestion));
    }

    @Transactional
    public SuggestionDto planSuggestion(Long suggestionId, Long customerId, LocalDate targetDate) {
        Suggestion suggestion = suggestionRepository.findByIdAndCustomerId(suggestionId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Suggestion not found: " + suggestionId));

        if (suggestion.getStatus() != SuggestionStatus.WISHLIST) {
            throw new IllegalArgumentException("Only wishlist suggestions can be planned");
        }

        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        if (targetDate != null) {
            suggestion.setSuggestedDate(targetDate);
        }
        return SuggestionDto.from(suggestionRepository.save(suggestion));
    }

    // ─── Learning ────────────────────────────────────────────────────────────

    @Transactional
    public void refreshLearnedProfile(Long customerId) {
        customerRepository.findById(customerId).ifPresent(customer -> {
            List<Suggestion> allFeedback = suggestionRepository.findRecentFeedback(
                    customerId, PageRequest.of(0, 50));
            if (allFeedback.isEmpty()) return;

            CustomerPreferences prefs = preferencesRepository.findByCustomer(customer).orElse(null);
            String learned = openAIService.generateLearnedProfile(customer, prefs, allFeedback);
            if (learned != null && prefs != null) {
                prefs.setLearnedProfile(learned);
                preferencesRepository.save(prefs);
                log.info("Updated learned profile for customer {}", customerId);
            }
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Suggestion buildSuggestion(Customer customer, SuggestionCategory category,
                                        OpenAIService.SuggestionData data) {
        Suggestion s = new Suggestion();
        s.setCustomer(customer);
        s.setCategory(category);
        s.setTitle(data.title());
        s.setDescription(data.description());
        s.setLocation(data.location());
        s.setEstimatedCost(data.estimatedCost());
        s.setSuggestedDate(parseDateSafely(data.suggestedDate(), category));
        s.setReasonForSuggestion(reasonOrDefault(data.reasonForSuggestion()));
        s.setStatus(SuggestionStatus.PENDING);
        s.setNotificationRead(false);
        return s;
    }

    private String reasonOrDefault(String reason) {
        return (reason == null || reason.isBlank()) ? DEFAULT_REASON : reason;
    }

    private LocalDate parseDateSafely(String dateStr, SuggestionCategory category) {
        try {
            if (dateStr != null && !dateStr.isBlank()) return LocalDate.parse(dateStr);
        } catch (Exception ignored) {}
        return switch (category) {
            case DAILY -> LocalDate.now().plusDays(1);
            case WEEKEND -> LocalDate.now().plusDays(3);
            case MONTHLY -> LocalDate.now().plusWeeks(2);
        };
    }
}