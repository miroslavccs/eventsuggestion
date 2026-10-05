package com.codecraft.eventsuggestion.dto;

import com.codecraft.eventsuggestion.domain.Address;
import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import com.codecraft.eventsuggestion.domain.enums.Gender;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;

import java.util.List;

public record CustomerProfileDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        Gender gender,
        Integer age,
        AddressDto address,
        String education,
        String currentEmployment,
        // Preferences
        List<String> sports,
        List<String> hobbies,
        List<String> interests,
        boolean likesTraveling,
        boolean likesNightlife,
        String additionalNotes,
        boolean vacationMode,
        List<SuggestionCategory> pausedCategories,
        // Read-only (ignored on PUT)
        String learnedProfile,
        int responsesUntilRefresh
) {
    public record AddressDto(String street, String city, String state, String country, String zipCode) {
        public static AddressDto from(Address a) {
            if (a == null) return null;
            return new AddressDto(a.getStreet(), a.getCity(), a.getState(), a.getCountry(), a.getZipCode());
        }
    }

    public static CustomerProfileDto from(Customer c, CustomerPreferences p, int responsesUntilRefresh) {
        return new CustomerProfileDto(
                c.getId(),
                c.getEmail(),
                c.getFirstName(),
                c.getLastName(),
                c.getGender(),
                c.getAge(),
                AddressDto.from(c.getAddress()),
                c.getEducation(),
                c.getCurrentEmployment(),
                p != null ? p.getSports() : List.of(),
                p != null ? p.getHobbies() : List.of(),
                p != null ? p.getInterests() : List.of(),
                p != null && p.isLikesTraveling(),
                p != null && p.isLikesNightlife(),
                p != null ? p.getAdditionalNotes() : null,
                p != null && p.isVacationMode(),
                p != null ? List.copyOf(p.getPausedCategories()) : List.of(),
                p != null ? p.getLearnedProfile() : null,
                responsesUntilRefresh
        );
    }
}