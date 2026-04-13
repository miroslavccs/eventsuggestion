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

    List<Suggestion> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Suggestion> findByCustomerIdAndCategoryOrderByCreatedAtDesc(Long customerId, SuggestionCategory category);

    List<Suggestion> findByCustomerIdAndNotificationReadFalseOrderByCreatedAtDesc(Long customerId);

    Optional<Suggestion> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerIdAndStatusNot(Long customerId, SuggestionStatus status);

    @Query("SELECT s FROM Suggestion s WHERE s.customer.id = :customerId AND s.status <> 'PENDING' ORDER BY s.respondedAt DESC")
    List<Suggestion> findRecentFeedback(@Param("customerId") Long customerId, Pageable pageable);
}