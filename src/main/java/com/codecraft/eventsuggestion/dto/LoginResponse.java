package com.codecraft.eventsuggestion.dto;

public record LoginResponse(String token, String email, String firstName, String lastName) {}