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
        var content = s.getContent();
        return new SuggestionDto(
                s.getId(),
                content.getCategory(),
                content.getTitle(),
                content.getDescription(),
                content.getLocation(),
                content.getEstimatedCost(),
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