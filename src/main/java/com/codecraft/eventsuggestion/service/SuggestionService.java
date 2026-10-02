package com.codecraft.eventsuggestion.service;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.SuggestionContent;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;
import com.codecraft.eventsuggestion.dto.FeedbackRequest;
import com.codecraft.eventsuggestion.dto.SuggestionDto;
import com.codecraft.eventsuggestion.repository.CustomerPreferencesRepository;
import com.codecraft.eventsuggestion.repository.CustomerRepository;
import com.codecraft.eventsuggestion.repository.SuggestionContentRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);
    private static final int SUGGESTIONS_PER_RUN = 3;
    private static final int MAX_CLUSTER_SUGGESTIONS = 10;
    private static final int FEEDBACK_HISTORY_LIMIT = 30;
    private static final int LEARNING_UPDATE_INTERVAL = 5;
    private static final String DEFAULT_REASON = "Personalized pick based on your profile and preferences";

    private final SuggestionRepository suggestionRepository;
    private final SuggestionContentRepository suggestionContentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPreferencesRepository preferencesRepository;
    private final OpenAIService openAIService;

    public SuggestionService(SuggestionRepository suggestionRepository,
                             SuggestionContentRepository suggestionContentRepository,
                             CustomerRepository customerRepository,
                             CustomerPreferencesRepository preferencesRepository,
                             OpenAIService openAIService) {
        this.suggestionRepository = suggestionRepository;
        this.suggestionContentRepository = suggestionContentRepository;
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
        Map<String, List<Customer>> clusters = new LinkedHashMap<>();
        List<Customer> individual = new ArrayList<>();

        for (Customer customer : customerRepository.findAll()) {
            try {
                if (isPaused(customer, category)) continue;
                String city = cityOf(customer);
                if (city == null) {
                    individual.add(customer);
                } else {
                    clusters.computeIfAbsent(city, k -> new ArrayList<>()).add(customer);
                }
            } catch (Exception e) {
                log.error("Failed checking {} generation eligibility for customer {}", category, customer.getId(), e);
            }
        }

        clusters.forEach((city, group) -> {
            try {
                generateForCluster(city, group, category);
            } catch (Exception e) {
                log.error("Failed generating {} cluster suggestions for {}", category, city, e);
            }
        });

        individual.forEach(customer -> {
            try {
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

    private String cityOf(Customer customer) {
        if (customer.getAddress() == null) return null;
        String city = customer.getAddress().getCity();
        return (city == null || city.isBlank()) ? null : city;
    }

    // ─── Clustered generation (scheduled batch jobs only) ─────────────────────

    private void generateForCluster(String city, List<Customer> customers, SuggestionCategory category) {
        int count = suggestionCountForCluster(customers.size());
        List<OpenAIService.SuggestionData> aiData = openAIService.generateClusterSuggestions(city, category, count);
        if (aiData.isEmpty()) return;

        List<SuggestionContent> contents = suggestionContentRepository.saveAll(
                aiData.stream().map(data -> buildContent(category, city, data)).toList());

        List<Suggestion> assignments = new ArrayList<>();
        for (Customer customer : customers) {
            CustomerPreferences prefs = preferencesRepository.findByCustomer(customer).orElse(null);
            for (SuggestionContent content : contents) {
                assignments.add(buildAssignment(customer, content, templatedReason(content, prefs)));
            }
        }
        suggestionRepository.saveAll(assignments);
    }

    private int suggestionCountForCluster(int clusterSize) {
        if (clusterSize >= 100) return MAX_CLUSTER_SUGGESTIONS;
        if (clusterSize >= 20) return 7;
        if (clusterSize >= 5) return 5;
        return SUGGESTIONS_PER_RUN;
    }

    private String templatedReason(SuggestionContent content, CustomerPreferences prefs) {
        if (prefs != null) {
            String match = matchingInterest(content, prefs);
            if (match != null) {
                return "Picked for you based on your interest in " + match;
            }
        }
        return DEFAULT_REASON;
    }

    private String matchingInterest(SuggestionContent content, CustomerPreferences prefs) {
        String haystack = (nullToEmpty(content.getTitle()) + " " + nullToEmpty(content.getDescription())).toLowerCase();
        return Stream.of(prefs.getSports(), prefs.getHobbies(), prefs.getInterests())
                .flatMap(List::stream)
                .filter(term -> term != null && !term.isBlank())
                .filter(term -> haystack.contains(term.toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ─── On-demand generation (e.g., from controller or after registration) ──

    @Transactional
    public List<SuggestionDto> generateForCustomer(Customer customer, SuggestionCategory category) {
        CustomerPreferences prefs = preferencesRepository.findByCustomer(customer).orElse(null);
        List<Suggestion> recentFeedback = suggestionRepository.findRecentFeedback(
                customer.getId(), PageRequest.of(0, FEEDBACK_HISTORY_LIMIT));

        List<OpenAIService.SuggestionData> aiData =
                openAIService.generateSuggestions(customer, prefs, recentFeedback, category, SUGGESTIONS_PER_RUN);

        List<SuggestionContent> contents = suggestionContentRepository.saveAll(
                aiData.stream().map(data -> buildContent(category, cityOf(customer), data)).toList());

        List<Suggestion> assignments = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            assignments.add(buildAssignment(customer, contents.get(i), reasonOrDefault(aiData.get(i).reasonForSuggestion())));
        }

        return suggestionRepository.saveAll(assignments).stream()
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

    private SuggestionContent buildContent(SuggestionCategory category, String city, OpenAIService.SuggestionData data) {
        SuggestionContent c = new SuggestionContent();
        c.setCategory(category);
        c.setTitle(data.title());
        c.setDescription(data.description());
        c.setLocation(data.location());
        c.setEstimatedCost(data.estimatedCost());
        c.setSuggestedDate(parseDateSafely(data.suggestedDate(), category));
        c.setCity(city);
        return c;
    }

    private Suggestion buildAssignment(Customer customer, SuggestionContent content, String reason) {
        Suggestion s = new Suggestion();
        s.setCustomer(customer);
        s.setContent(content);
        s.setSuggestedDate(content.getSuggestedDate());
        s.setReasonForSuggestion(reason);
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