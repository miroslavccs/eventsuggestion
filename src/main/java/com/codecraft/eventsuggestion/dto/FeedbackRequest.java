package com.codecraft.eventsuggestion.dto;

import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotNull SuggestionStatus status,
        String comment
) {}