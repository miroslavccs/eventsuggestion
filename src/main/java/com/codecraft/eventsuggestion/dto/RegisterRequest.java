package com.codecraft.eventsuggestion.dto;

import com.codecraft.eventsuggestion.domain.enums.Gender;
import jakarta.validation.constraints.*;

import java.util.List;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        Gender gender,
        @Min(13) @Max(120) Integer age,
        AddressDto address,
        String education,
        String currentEmployment,
        // Preferences
        List<String> sports,
        List<String> hobbies,
        List<String> artInterests,
        boolean likesTraveling,
        boolean likesNightlife,
        String additionalNotes
) {
    public record AddressDto(String street, String city, String state, String country, String zipCode) {}
}