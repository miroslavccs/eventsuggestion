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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private SuggestionRepository suggestionRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerPreferencesRepository preferencesRepository;
    @Mock
    private OpenAIService openAIService;

    private SuggestionService newService() {
        return new SuggestionService(suggestionRepository, customerRepository, preferencesRepository, openAIService);
    }

    private Customer customer(String email) {
        Customer c = new Customer();
        c.setEmail(email);
        c.setFirstName("Jane");
        c.setLastName("Doe");
        return c;
    }

    // ─── generateForCustomer ────────────────────────────────────────────────

    @Test
    void generateForCustomer_mapsAiDataToPendingUnreadSuggestions() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(eq(customer), isNull(), anyList(), eq(SuggestionCategory.DAILY), eq(3)))
                .thenReturn(List.of(new OpenAIService.SuggestionData(
                        "Jazz night", "Live jazz", "London", "$20", "2027-01-01", "Matches interests")));
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SuggestionDto> result = service.generateForCustomer(customer, SuggestionCategory.DAILY);

        assertThat(result).hasSize(1);
        ArgumentCaptor<List<Suggestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(suggestionRepository).saveAll(captor.capture());
        Suggestion saved = captor.getValue().get(0);
        assertThat(saved.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(saved.isNotificationRead()).isFalse();
        assertThat(saved.getCategory()).isEqualTo(SuggestionCategory.DAILY);
        assertThat(saved.getTitle()).isEqualTo("Jazz night");
        assertThat(saved.getSuggestedDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void generateForCustomer_emptyAiResponse_savesAndReturnsEmptyList() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(any(), any(), anyList(), any(), anyInt())).thenReturn(List.of());
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SuggestionDto> result = service.generateForCustomer(customer, SuggestionCategory.WEEKEND);

        assertThat(result).isEmpty();
        verify(suggestionRepository).saveAll(List.of());
    }

    @Test
    void generateForCustomer_validDateString_isParsed() {
        assertThat(dateFor(SuggestionCategory.DAILY, "2030-06-15")).isEqualTo(LocalDate.of(2030, 6, 15));
    }

    @Test
    void generateForCustomer_nullDateString_fallsBackPerCategory() {
        assertThat(dateFor(SuggestionCategory.DAILY, null)).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(dateFor(SuggestionCategory.WEEKEND, null)).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(dateFor(SuggestionCategory.MONTHLY, null)).isEqualTo(LocalDate.now().plusWeeks(2));
    }

    @Test
    void generateForCustomer_blankDateString_fallsBackPerCategory() {
        assertThat(dateFor(SuggestionCategory.DAILY, "  ")).isEqualTo(LocalDate.now().plusDays(1));
    }

    @Test
    void generateForCustomer_unparsableDateString_fallsBackPerCategory() {
        assertThat(dateFor(SuggestionCategory.MONTHLY, "not-a-date")).isEqualTo(LocalDate.now().plusWeeks(2));
    }

    private LocalDate dateFor(SuggestionCategory category, String suggestedDate) {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(any(), any(), anyList(), any(), anyInt()))
                .thenReturn(List.of(new OpenAIService.SuggestionData("T", "D", "L", "C", suggestedDate, "R")));
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SuggestionDto> result = service.generateForCustomer(customer, category);
        return result.get(0).suggestedDate();
    }

    // ─── getSuggestions / getNotifications ──────────────────────────────────

    @Test
    void getSuggestions_nullCategory_usesFindAllForCustomer() {
        SuggestionService service = newService();
        when(suggestionRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(new Suggestion()));

        List<SuggestionDto> result = service.getSuggestions(1L, null);

        assertThat(result).hasSize(1);
        verify(suggestionRepository, never()).findByCustomerIdAndCategoryOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getSuggestions_withCategory_usesCategoryFilteredQuery() {
        SuggestionService service = newService();
        when(suggestionRepository.findByCustomerIdAndCategoryOrderByCreatedAtDesc(1L, SuggestionCategory.DAILY))
                .thenReturn(List.of(new Suggestion()));

        List<SuggestionDto> result = service.getSuggestions(1L, SuggestionCategory.DAILY);

        assertThat(result).hasSize(1);
        verify(suggestionRepository, never()).findByCustomerIdOrderByCreatedAtDesc(any());
    }

    @Test
    void getNotifications_mapsUnreadSuggestions() {
        SuggestionService service = newService();
        when(suggestionRepository.findActiveNotifications(1L))
                .thenReturn(List.of(new Suggestion()));

        assertThat(service.getNotifications(1L)).hasSize(1);
    }

    // ─── provideFeedback ─────────────────────────────────────────────────────

    @Test
    void provideFeedback_pendingStatus_throwsAndNeverQueriesOrSaves() {
        SuggestionService service = newService();

        assertThatThrownBy(() -> service.provideFeedback(1L, 1L, new FeedbackRequest(SuggestionStatus.PENDING, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(suggestionRepository, never()).findByIdAndCustomerId(any(), any());
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void provideFeedback_notFound_throwsEntityNotFound() {
        SuggestionService service = newService();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provideFeedback(1L, 1L, new FeedbackRequest(SuggestionStatus.ACCEPTED, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void provideFeedback_happyPath_setsFieldsBeforeSaving() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.countByCustomerIdAndStatusNot(1L, SuggestionStatus.PENDING)).thenReturn(3L);

        service.provideFeedback(1L, 1L, new FeedbackRequest(SuggestionStatus.ACCEPTED, "Loved it"));

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(suggestion.getFeedbackComment()).isEqualTo("Loved it");
        assertThat(suggestion.isNotificationRead()).isTrue();
        assertThat(suggestion.getRespondedAt()).isNotNull();
    }

    @Test
    void provideFeedback_countIsMultipleOfFive_triggersLearningRefresh() {
        SuggestionService service = spy(newService());
        Suggestion suggestion = new Suggestion();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.countByCustomerIdAndStatusNot(1L, SuggestionStatus.PENDING)).thenReturn(5L);

        service.provideFeedback(1L, 1L, new FeedbackRequest(SuggestionStatus.ACCEPTED, null));

        verify(service).refreshLearnedProfile(1L);
    }

    @Test
    void provideFeedback_countIsNotMultipleOfFive_doesNotTriggerLearningRefresh() {
        SuggestionService service = spy(newService());
        Suggestion suggestion = new Suggestion();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.countByCustomerIdAndStatusNot(1L, SuggestionStatus.PENDING)).thenReturn(3L);

        service.provideFeedback(1L, 1L, new FeedbackRequest(SuggestionStatus.ACCEPTED, null));

        verify(service, never()).refreshLearnedProfile(anyLong());
    }

    // ─── refreshLearnedProfile ───────────────────────────────────────────────

    @Test
    void refreshLearnedProfile_customerNotFound_isNoOp() {
        SuggestionService service = newService();
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        service.refreshLearnedProfile(1L);

        verifyNoInteractions(openAIService);
        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void refreshLearnedProfile_emptyFeedback_returnsBeforeCallingOpenAI() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(suggestionRepository.findRecentFeedback(eq(1L), any(Pageable.class))).thenReturn(List.of());

        service.refreshLearnedProfile(1L);

        verifyNoInteractions(openAIService);
    }

    @Test
    void refreshLearnedProfile_openAiReturnsNull_doesNotSave() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        Suggestion feedback = new Suggestion();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(suggestionRepository.findRecentFeedback(eq(1L), any(Pageable.class))).thenReturn(List.of(feedback));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(new CustomerPreferences()));
        when(openAIService.generateLearnedProfile(eq(customer), any(), anyList())).thenReturn(null);

        service.refreshLearnedProfile(1L);

        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void refreshLearnedProfile_noPreferencesRow_doesNotSaveEvenIfOpenAiReturnsText() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        Suggestion feedback = new Suggestion();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(suggestionRepository.findRecentFeedback(eq(1L), any(Pageable.class))).thenReturn(List.of(feedback));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(openAIService.generateLearnedProfile(eq(customer), isNull(), anyList())).thenReturn("insight");

        service.refreshLearnedProfile(1L);

        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void refreshLearnedProfile_happyPath_savesLearnedProfileText() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        Suggestion feedback = new Suggestion();
        CustomerPreferences prefs = new CustomerPreferences();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(suggestionRepository.findRecentFeedback(eq(1L), any(Pageable.class))).thenReturn(List.of(feedback));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(prefs));
        when(openAIService.generateLearnedProfile(eq(customer), eq(prefs), anyList())).thenReturn("likes jazz");

        service.refreshLearnedProfile(1L);

        assertThat(prefs.getLearnedProfile()).isEqualTo("likes jazz");
        verify(preferencesRepository).save(prefs);
    }

    // ─── markNotificationRead ────────────────────────────────────────────────

    @Test
    void markNotificationRead_found_marksReadAndSaves() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));

        service.markNotificationRead(1L, 1L);

        assertThat(suggestion.isNotificationRead()).isTrue();
        verify(suggestionRepository).save(suggestion);
    }

    @Test
    void markNotificationRead_notFound_isNoOp() {
        SuggestionService service = newService();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.empty());

        service.markNotificationRead(1L, 1L);

        verify(suggestionRepository, never()).save(any());
    }

    // ─── snoozeSuggestion ────────────────────────────────────────────────────

    @Test
    void snoozeSuggestion_pendingSuggestion_setsSnoozedUntil() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.PENDING);
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate until = LocalDate.now().plusDays(3);
        SuggestionDto result = service.snoozeSuggestion(1L, 1L, until);

        assertThat(result.snoozedUntil()).isEqualTo(until);
        assertThat(suggestion.getSnoozedUntil()).isEqualTo(until);
    }

    @Test
    void snoozeSuggestion_notFound_throwsEntityNotFound() {
        SuggestionService service = newService();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.snoozeSuggestion(1L, 1L, LocalDate.now().plusDays(1)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void snoozeSuggestion_notPending_throwsAndNeverSaves() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.snoozeSuggestion(1L, 1L, LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(suggestionRepository, never()).save(any());
    }

    // ─── rateSuggestion ──────────────────────────────────────────────────────

    @Test
    void rateSuggestion_acceptedAndPastDated_setsRating() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        suggestion.setSuggestedDate(LocalDate.now().minusDays(1));
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        SuggestionDto result = service.rateSuggestion(1L, 1L, 5);

        assertThat(result.rating()).isEqualTo(5);
        assertThat(suggestion.getRating()).isEqualTo(5);
    }

    @Test
    void rateSuggestion_notFound_throwsEntityNotFound() {
        SuggestionService service = newService();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rateSuggestion(1L, 1L, 5))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void rateSuggestion_notAccepted_throwsAndNeverSaves() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.PENDING);
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.rateSuggestion(1L, 1L, 5))
                .isInstanceOf(IllegalArgumentException.class);

        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void rateSuggestion_dateNotYetPassed_throwsAndNeverSaves() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        suggestion.setSuggestedDate(LocalDate.now().plusDays(1));
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.rateSuggestion(1L, 1L, 5))
                .isInstanceOf(IllegalArgumentException.class);

        verify(suggestionRepository, never()).save(any());
    }

    // ─── planSuggestion ──────────────────────────────────────────────────────

    @Test
    void planSuggestion_wishlistWithTargetDate_promotesAndSetsDate() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.WISHLIST);
        suggestion.setSuggestedDate(LocalDate.now().plusDays(1));
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate target = LocalDate.now().plusDays(10);
        SuggestionDto result = service.planSuggestion(1L, 1L, target);

        assertThat(result.status()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(result.suggestedDate()).isEqualTo(target);
    }

    @Test
    void planSuggestion_wishlistWithoutTargetDate_promotesAndKeepsExistingDate() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.WISHLIST);
        LocalDate existingDate = LocalDate.now().plusDays(5);
        suggestion.setSuggestedDate(existingDate);
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        SuggestionDto result = service.planSuggestion(1L, 1L, null);

        assertThat(result.status()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(result.suggestedDate()).isEqualTo(existingDate);
    }

    @Test
    void planSuggestion_notFound_throwsEntityNotFound() {
        SuggestionService service = newService();
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.planSuggestion(1L, 1L, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void planSuggestion_notWishlist_throwsAndNeverSaves() {
        SuggestionService service = newService();
        Suggestion suggestion = new Suggestion();
        suggestion.setStatus(SuggestionStatus.PENDING);
        when(suggestionRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.planSuggestion(1L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(suggestionRepository, never()).save(any());
    }

    // ─── Reason fallback ─────────────────────────────────────────────────────

    @Test
    void generateForCustomer_blankReason_fallsBackToDefaultReason() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(any(), any(), anyList(), any(), anyInt()))
                .thenReturn(List.of(new OpenAIService.SuggestionData("T", "D", "L", "C", null, "  ")));
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SuggestionDto> result = service.generateForCustomer(customer, SuggestionCategory.DAILY);

        assertThat(result.get(0).reasonForSuggestion())
                .isEqualTo("Personalized pick based on your profile and preferences");
    }

    @Test
    void generateForCustomer_nonBlankReason_isPassedThroughUnchanged() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(any(), any(), anyList(), any(), anyInt()))
                .thenReturn(List.of(new OpenAIService.SuggestionData("T", "D", "L", "C", null, "Matches your interests")));
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SuggestionDto> result = service.generateForCustomer(customer, SuggestionCategory.DAILY);

        assertThat(result.get(0).reasonForSuggestion()).isEqualTo("Matches your interests");
    }

    // ─── Vacation mode / paused categories ───────────────────────────────────

    @Test
    void generateDailySuggestions_vacationMode_skipsCustomerEntirely() {
        SuggestionService service = newService();
        Customer customer = customer("vacation@example.com");
        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setVacationMode(true);
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(prefs));

        service.generateDailySuggestions();

        verifyNoInteractions(openAIService);
        verify(suggestionRepository, never()).saveAll(any());
    }

    @Test
    void generateDailySuggestions_categoryPaused_skipsCustomerForThatCategoryOnly() {
        SuggestionService service = newService();
        Customer customer = customer("paused-daily@example.com");
        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setPausedCategories(Set.of(SuggestionCategory.DAILY));
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(prefs));

        service.generateDailySuggestions();

        verifyNoInteractions(openAIService);
    }

    @Test
    void generateDailySuggestions_notPaused_generatesNormally() {
        SuggestionService service = newService();
        Customer customer = customer("active@example.com");
        CustomerPreferences prefs = new CustomerPreferences();
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(prefs));
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(eq(customer), eq(prefs), anyList(), eq(SuggestionCategory.DAILY), eq(3)))
                .thenReturn(List.of());
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.generateDailySuggestions();

        verify(openAIService).generateSuggestions(eq(customer), eq(prefs), anyList(), eq(SuggestionCategory.DAILY), eq(3));
    }

    // ─── Scheduled batch resilience ──────────────────────────────────────────

    @Test
    void generateDailySuggestions_oneCustomerFailing_doesNotStopOthers() {
        SuggestionService service = newService();
        Customer failing = customer("fail@example.com");
        Customer succeeding = customer("ok@example.com");
        when(customerRepository.findAll()).thenReturn(List.of(failing, succeeding));

        when(preferencesRepository.findByCustomer(failing)).thenThrow(new RuntimeException("boom"));
        when(preferencesRepository.findByCustomer(succeeding)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(eq(succeeding), isNull(), anyList(), eq(SuggestionCategory.DAILY), eq(3)))
                .thenReturn(List.of(new OpenAIService.SuggestionData("T", "D", "L", "C", null, "R")));
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(service::generateDailySuggestions).doesNotThrowAnyException();

        verify(suggestionRepository, times(1)).saveAll(anyList());
        verify(openAIService, never()).generateSuggestions(eq(failing), any(), anyList(), any(), anyInt());
    }

    @Test
    void generateWeekendSuggestions_passesWeekendCategoryToOpenAI() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(any(), any(), anyList(), any(), anyInt())).thenReturn(List.of());
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.generateWeekendSuggestions();

        verify(openAIService).generateSuggestions(eq(customer), isNull(), anyList(), eq(SuggestionCategory.WEEKEND), eq(3));
    }

    @Test
    void generateMonthlySuggestions_passesMonthlyCategoryToOpenAI() {
        SuggestionService service = newService();
        Customer customer = customer("jane@example.com");
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(suggestionRepository.findRecentFeedback(any(), any(Pageable.class))).thenReturn(List.of());
        when(openAIService.generateSuggestions(any(), any(), anyList(), any(), anyInt())).thenReturn(List.of());
        when(suggestionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.generateMonthlySuggestions();

        verify(openAIService).generateSuggestions(eq(customer), isNull(), anyList(), eq(SuggestionCategory.MONTHLY), eq(3));
    }
}
