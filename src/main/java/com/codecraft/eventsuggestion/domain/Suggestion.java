package com.codecraft.eventsuggestion.domain;

import com.codecraft.eventsuggestion.domain.enums.SuggestionStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suggestions")
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** The shared, AI-generated content this suggestion points at — may be shared with other customers. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private SuggestionContent content;

    /**
     * Per-customer effective date — initialized from {@link SuggestionContent#getSuggestedDate()} at
     * assignment time, but independently mutable (e.g. via the plan action), so it must not live on
     * the shared content.
     */
    private LocalDate suggestedDate;

    @Column(length = 1000)
    private String reasonForSuggestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    /** Customer's feedback comment — supplied on accept, reject, or wishlist */
    @Column(length = 1000)
    private String feedbackComment;

    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    /** False until the customer opens/dismisses the notification */
    private boolean notificationRead = false;

    /** If set and in the future, the suggestion is hidden from the notification feed until this date */
    private LocalDate snoozedUntil;

    /** 1-5 customer rating, settable once the suggestion is ACCEPTED and its date has passed */
    private Integer rating;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public SuggestionContent getContent() { return content; }
    public void setContent(SuggestionContent content) { this.content = content; }

    public LocalDate getSuggestedDate() { return suggestedDate; }
    public void setSuggestedDate(LocalDate suggestedDate) { this.suggestedDate = suggestedDate; }

    public String getReasonForSuggestion() { return reasonForSuggestion; }
    public void setReasonForSuggestion(String reasonForSuggestion) { this.reasonForSuggestion = reasonForSuggestion; }

    public SuggestionStatus getStatus() { return status; }
    public void setStatus(SuggestionStatus status) { this.status = status; }

    public String getFeedbackComment() { return feedbackComment; }
    public void setFeedbackComment(String feedbackComment) { this.feedbackComment = feedbackComment; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public boolean isNotificationRead() { return notificationRead; }
    public void setNotificationRead(boolean notificationRead) { this.notificationRead = notificationRead; }

    public LocalDate getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(LocalDate snoozedUntil) { this.snoozedUntil = snoozedUntil; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
}