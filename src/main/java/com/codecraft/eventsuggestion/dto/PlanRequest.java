package com.codecraft.eventsuggestion.dto;

import jakarta.validation.constraints.Future;

import java.time.LocalDate;

public record PlanRequest(
        @Future LocalDate targetDate
) {}
