package com.codecraft.eventsuggestion.domain;

import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The AI-generated, shareable part of a suggestion. Immutable after creation; multiple customers'
 * {@link Suggestion} assignments can point at the same content row to avoid redundant AI calls.
 */
@Entity
@Table(name = "suggestion_contents")
public class SuggestionContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionCategory category;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String location;
    private String estimatedCost;
    private LocalDate suggestedDate;

    /** The cluster key this content was generated for; null for individually-generated content. */
    private String city;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public SuggestionCategory getCategory() { return category; }
    public void setCategory(SuggestionCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(String estimatedCost) { this.estimatedCost = estimatedCost; }

    public LocalDate getSuggestedDate() { return suggestedDate; }
    public void setSuggestedDate(LocalDate suggestedDate) { this.suggestedDate = suggestedDate; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
