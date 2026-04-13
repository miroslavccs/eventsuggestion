package com.codecraft.eventsuggestion.controller;

import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.dto.FeedbackRequest;
import com.codecraft.eventsuggestion.dto.SuggestionDto;
import com.codecraft.eventsuggestion.service.CustomerService;
import com.codecraft.eventsuggestion.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
@Tag(name = "Suggestions", description = "AI-generated personalised activity and event suggestions")
@SecurityRequirement(name = "bearerAuth")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final CustomerService customerService;

    public SuggestionController(SuggestionService suggestionService, CustomerService customerService) {
        this.suggestionService = suggestionService;
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(
        summary = "List suggestions",
        description = "Returns all suggestions for the authenticated customer. Filter by category to narrow results.",
        responses = @ApiResponse(responseCode = "200", description = "Suggestions returned")
    )
    public ResponseEntity<List<SuggestionDto>> getSuggestions(
            @AuthenticationPrincipal UserDetails user,
            @Parameter(description = "Filter by category: DAILY, WEEKEND, or MONTHLY")
            @RequestParam(required = false) SuggestionCategory category) {

        Long customerId = customerService.findByEmail(user.getUsername()).getId();
        return ResponseEntity.ok(suggestionService.getSuggestions(customerId, category));
    }

    @GetMapping("/notifications")
    @Operation(
        summary = "Notification feed",
        description = "Returns all suggestions that have not yet been read or acted upon. Poll this endpoint to show new notifications.",
        responses = @ApiResponse(responseCode = "200", description = "Unread suggestions returned")
    )
    public ResponseEntity<List<SuggestionDto>> getNotifications(@AuthenticationPrincipal UserDetails user) {
        Long customerId = customerService.findByEmail(user.getUsername()).getId();
        return ResponseEntity.ok(suggestionService.getNotifications(customerId));
    }

    @PostMapping("/generate")
    @Operation(
        summary = "Manually trigger suggestion generation",
        description = "Calls OpenAI to generate new suggestions for the given category and saves them. Useful for testing or after updating preferences.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Suggestions generated and returned"),
            @ApiResponse(responseCode = "200", description = "Empty list if OpenAI API key is not configured")
        }
    )
    public ResponseEntity<List<SuggestionDto>> generateSuggestions(
            @AuthenticationPrincipal UserDetails user,
            @Parameter(description = "Category to generate (defaults to DAILY)")
            @RequestParam(defaultValue = "DAILY") SuggestionCategory category) {

        var customer = customerService.findByEmail(user.getUsername());
        return ResponseEntity.ok(suggestionService.generateForCustomer(customer, category));
    }

    @PutMapping("/{id}/feedback")
    @Operation(
        summary = "Submit feedback on a suggestion",
        description = """
                Set the outcome of a suggestion:
                - **ACCEPTED** — customer is going / went.
                - **REJECTED** — not interested; add a comment to help the AI learn why.
                - **WISHLIST** — nice idea but not currently possible.

                Every 5th feedback triggers an automatic update of the customer's learned preference profile.
                """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Feedback recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "404", description = "Suggestion not found")
        }
    )
    public ResponseEntity<SuggestionDto> provideFeedback(
            @Parameter(description = "Suggestion ID") @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody FeedbackRequest request) {

        Long customerId = customerService.findByEmail(user.getUsername()).getId();
        return ResponseEntity.ok(suggestionService.provideFeedback(id, customerId, request));
    }

    @PutMapping("/{id}/read")
    @Operation(
        summary = "Mark notification as read",
        description = "Dismisses the notification badge without changing the suggestion's status.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Marked as read"),
            @ApiResponse(responseCode = "404", description = "Suggestion not found")
        }
    )
    public ResponseEntity<Void> markRead(
            @Parameter(description = "Suggestion ID") @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {

        Long customerId = customerService.findByEmail(user.getUsername()).getId();
        suggestionService.markNotificationRead(id, customerId);
        return ResponseEntity.noContent().build();
    }
}