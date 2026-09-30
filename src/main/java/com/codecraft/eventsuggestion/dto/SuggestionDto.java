package com.codecraft.eventsuggestion.dto;

import com.codecraft.eventsuggestion.domain.Suggestion;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SuggestionDto(
        Long id,
        SuggestionCategory category,
        String title,
        String description,
        String location,
        String estimatedCost,
        LocalDate suggestedDate,
        String reasonForSuggestion,
        SuggestionStatus status,
        String feedbackComment,
        LocalDateTime createdAt,
        LocalDateTime respondedAt,
        boolean notificationRead,
        Integer rating,
        LocalDate snoozedUntil
) {
    public static SuggestionDto from(Suggestion s) {
        return new SuggestionDto(
                s.getId(),
                s.getCategory(),
                s.getTitle(),
                s.getDescription(),
                s.getLocation(),
                s.getEstimatedCost(),
                s.getSuggestedDate(),
                s.getReasonForSuggestion(),
                s.getStatus(),
                s.getFeedbackComment(),
                s.getCreatedAt(),
                s.getRespondedAt(),
                s.isNotificationRead(),
                s.getRating(),
                s.getSnoozedUntil()
        );
    }
}