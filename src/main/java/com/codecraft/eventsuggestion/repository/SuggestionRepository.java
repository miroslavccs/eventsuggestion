package com.codecraft.eventsuggestion.repository;

import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    @Query("SELECT s FROM Suggestion s JOIN FETCH s.content WHERE s.customer.id = :customerId ORDER BY s.createdAt DESC")
    List<Suggestion> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    @Query("SELECT s FROM Suggestion s JOIN FETCH s.content WHERE s.customer.id = :customerId AND s.content.category = :category " +
           "ORDER BY s.createdAt DESC")
    List<Suggestion> findByCustomerIdAndCategoryOrderByCreatedAtDesc(@Param("customerId") Long customerId,
                                                                      @Param("category") SuggestionCategory category);

    @Query("SELECT s FROM Suggestion s JOIN FETCH s.content WHERE s.customer.id = :customerId AND s.notificationRead = false " +
           "AND (s.snoozedUntil IS NULL OR s.snoozedUntil <= CURRENT_DATE) ORDER BY s.createdAt DESC")
    List<Suggestion> findActiveNotifications(@Param("customerId") Long customerId);

    @Query("SELECT s FROM Suggestion s JOIN FETCH s.content WHERE s.id = :id AND s.customer.id = :customerId")
    Optional<Suggestion> findByIdAndCustomerId(@Param("id") Long id, @Param("customerId") Long customerId);

    long countByCustomerIdAndStatusNot(Long customerId, SuggestionStatus status);

    @Query("SELECT s FROM Suggestion s JOIN FETCH s.content WHERE s.customer.id = :customerId AND s.status <> 'PENDING' " +
           "ORDER BY s.respondedAt DESC")
    List<Suggestion> findRecentFeedback(@Param("customerId") Long customerId, Pageable pageable);
}
