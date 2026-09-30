package com.codecraft.eventsuggestion.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SnoozeRequest(
        @NotNull @Future LocalDate until
) {}
