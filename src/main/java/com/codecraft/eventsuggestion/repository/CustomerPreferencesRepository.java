package com.codecraft.eventsuggestion.repository;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerPreferencesRepository extends JpaRepository<CustomerPreferences, Long> {
    Optional<CustomerPreferences> findByCustomer(Customer customer);
    Optional<CustomerPreferences> findByCustomerId(Long customerId);
}