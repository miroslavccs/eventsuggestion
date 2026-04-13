package com.codecraft.eventsuggestion.domain.enums;

public enum SuggestionStatus {
    /** Suggestion delivered, customer has not responded yet */
    PENDING,
    /** Customer accepted — they plan to / did attend */
    ACCEPTED,
    /** Customer rejected — not interested (comment optional) */
    REJECTED,
    /** Nice to do but not currently possible */
    WISHLIST
}