package com.codecraft.eventsuggestion.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_preferences")
public class CustomerPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pref_sports", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "sport")
    private List<String> sports = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pref_hobbies", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "hobby")
    private List<String> hobbies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interests", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "interest")
    private List<String> interests = new ArrayList<>();

    private boolean likesTraveling;
    private boolean likesNightlife;

    @Column(length = 1000)
    private String additionalNotes;

    /**
     * JSON string summarising what the AI has learned about this customer
     * based on their accepted/rejected suggestions. Updated periodically.
     */
    @Column(length = 4000)
    private String learnedProfile;

    // Getters and setters

    public Long getId() { return id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<String> getSports() { return sports; }
    public void setSports(List<String> sports) { this.sports = sports; }

    public List<String> getHobbies() { return hobbies; }
    public void setHobbies(List<String> hobbies) { this.hobbies = hobbies; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }

    public boolean isLikesTraveling() { return likesTraveling; }
    public void setLikesTraveling(boolean likesTraveling) { this.likesTraveling = likesTraveling; }

    public boolean isLikesNightlife() { return likesNightlife; }
    public void setLikesNightlife(boolean likesNightlife) { this.likesNightlife = likesNightlife; }

    public String getAdditionalNotes() { return additionalNotes; }
    public void setAdditionalNotes(String additionalNotes) { this.additionalNotes = additionalNotes; }

    public String getLearnedProfile() { return learnedProfile; }
    public void setLearnedProfile(String learnedProfile) { this.learnedProfile = learnedProfile; }
}