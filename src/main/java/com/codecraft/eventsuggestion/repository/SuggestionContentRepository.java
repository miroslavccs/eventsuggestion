package com.codecraft.eventsuggestion.repository;

import com.codecraft.eventsuggestion.domain.SuggestionContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionContentRepository extends JpaRepository<SuggestionContent, Long> {
}
