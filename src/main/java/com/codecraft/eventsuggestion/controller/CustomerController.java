package com.codecraft.eventsuggestion.controller;

import com.codecraft.eventsuggestion.dto.CustomerProfileDto;
import com.codecraft.eventsuggestion.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customer Profile", description = "View and update your profile and preferences")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    @Operation(
        summary = "Get my profile",
        description = "Returns the authenticated customer's profile including preferences.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    public ResponseEntity<CustomerProfileDto> getMyProfile(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(customerService.getProfile(user.getUsername()));
    }

    @PutMapping("/me")
    @Operation(
        summary = "Update my profile",
        description = "Updates profile information and/or preferences. All fields are replaced — send the full object.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    public ResponseEntity<CustomerProfileDto> updateMyProfile(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody CustomerProfileDto dto) {
        return ResponseEntity.ok(customerService.updateProfile(user.getUsername(), dto));
    }
}