package com.codecraft.eventsuggestion.service;

import com.codecraft.eventsuggestion.domain.Address;
import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import com.codecraft.eventsuggestion.dto.CustomerProfileDto;
import com.codecraft.eventsuggestion.dto.LoginRequest;
import com.codecraft.eventsuggestion.dto.LoginResponse;
import com.codecraft.eventsuggestion.dto.RegisterRequest;
import com.codecraft.eventsuggestion.repository.CustomerPreferencesRepository;
import com.codecraft.eventsuggestion.repository.CustomerRepository;
import com.codecraft.eventsuggestion.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerPreferencesRepository preferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerPreferencesRepository preferencesRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.customerRepository = customerRepository;
        this.preferencesRepository = preferencesRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (customerRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already in use: " + req.email());
        }

        Customer customer = new Customer();
        customer.setEmail(req.email());
        customer.setPassword(passwordEncoder.encode(req.password()));
        customer.setFirstName(req.firstName());
        customer.setLastName(req.lastName());
        customer.setGender(req.gender());
        customer.setAge(req.age());
        customer.setEducation(req.education());
        customer.setCurrentEmployment(req.currentEmployment());

        if (req.address() != null) {
            customer.setAddress(new Address(
                    req.address().street(),
                    req.address().city(),
                    req.address().state(),
                    req.address().country(),
                    req.address().zipCode()
            ));
        }

        Customer saved = customerRepository.save(customer);

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setCustomer(saved);
        prefs.setSports(req.sports() != null ? req.sports() : List.of());
        prefs.setHobbies(req.hobbies() != null ? req.hobbies() : List.of());
        prefs.setInterests(req.interests() != null ? req.interests() : List.of());
        prefs.setLikesTraveling(req.likesTraveling());
        prefs.setLikesNightlife(req.likesNightlife());
        prefs.setAdditionalNotes(req.additionalNotes());
        preferencesRepository.save(prefs);

        String token = jwtUtil.generateToken(saved.getEmail());
        return new LoginResponse(token, saved.getEmail(), saved.getFirstName(), saved.getLastName());
    }

    public LoginResponse login(LoginRequest req) {
        Customer customer = customerRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), customer.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(customer.getEmail());
        return new LoginResponse(token, customer.getEmail(), customer.getFirstName(), customer.getLastName());
    }

    @Transactional(readOnly = true)
    public CustomerProfileDto getProfile(String email) {
        Customer customer = findByEmail(email);
        CustomerPreferences prefs = preferencesRepository.findByCustomer(customer).orElse(null);
        return CustomerProfileDto.from(customer, prefs);
    }

    @Transactional
    public CustomerProfileDto updateProfile(String email, CustomerProfileDto dto) {
        Customer customer = findByEmail(email);

        customer.setFirstName(dto.firstName());
        customer.setLastName(dto.lastName());
        customer.setGender(dto.gender());
        customer.setAge(dto.age());
        customer.setEducation(dto.education());
        customer.setCurrentEmployment(dto.currentEmployment());

        if (dto.address() != null) {
            customer.setAddress(new Address(
                    dto.address().street(),
                    dto.address().city(),
                    dto.address().state(),
                    dto.address().country(),
                    dto.address().zipCode()
            ));
        }

        customerRepository.save(customer);

        CustomerPreferences prefs = preferencesRepository.findByCustomer(customer)
                .orElseGet(() -> { CustomerPreferences p = new CustomerPreferences(); p.setCustomer(customer); return p; });

        prefs.setSports(dto.sports() != null ? dto.sports() : List.of());
        prefs.setHobbies(dto.hobbies() != null ? dto.hobbies() : List.of());
        prefs.setInterests(dto.interests() != null ? dto.interests() : List.of());
        prefs.setLikesTraveling(dto.likesTraveling());
        prefs.setLikesNightlife(dto.likesNightlife());
        prefs.setAdditionalNotes(dto.additionalNotes());
        prefs.setVacationMode(dto.vacationMode());
        prefs.setPausedCategories(dto.pausedCategories() != null ? new HashSet<>(dto.pausedCategories()) : Set.of());
        preferencesRepository.save(prefs);

        return CustomerProfileDto.from(customer, prefs);
    }

    public Customer findByEmail(String email) {
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Customer not found: " + email));
    }
}