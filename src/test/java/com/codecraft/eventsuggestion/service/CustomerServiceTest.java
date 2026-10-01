package com.codecraft.eventsuggestion.service;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.domain.CustomerPreferences;
import com.codecraft.eventsuggestion.domain.enums.SuggestionCategory;
import com.codecraft.eventsuggestion.dto.CustomerProfileDto;
import com.codecraft.eventsuggestion.dto.LoginRequest;
import com.codecraft.eventsuggestion.dto.LoginResponse;
import com.codecraft.eventsuggestion.dto.RegisterRequest;
import com.codecraft.eventsuggestion.repository.CustomerPreferencesRepository;
import com.codecraft.eventsuggestion.repository.CustomerRepository;
import com.codecraft.eventsuggestion.security.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerPreferencesRepository preferencesRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    private CustomerService customerService;

    private CustomerService newService() {
        return new CustomerService(customerRepository, preferencesRepository, passwordEncoder, jwtUtil);
    }

    private RegisterRequest registerRequest(RegisterRequest.AddressDto address,
                                             List<String> sports, List<String> hobbies, List<String> interests) {
        return new RegisterRequest(
                "jane@example.com", "secret123", "Jane", "Doe", null, 30,
                address, "MSc", "Engineer", sports, hobbies, interests, true, false, "notes");
    }

    private Customer savedCustomer() {
        Customer c = new Customer();
        c.setEmail("jane@example.com");
        c.setPassword("hashed");
        c.setFirstName("Jane");
        c.setLastName("Doe");
        return c;
    }

    @Test
    void register_happyPath_encodesPasswordSavesAndReturnsToken() {
        customerService = newService();
        when(customerRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer());
        when(jwtUtil.generateToken("jane@example.com")).thenReturn("token123");

        RegisterRequest req = registerRequest(
                new RegisterRequest.AddressDto("Street 1", "London", "State", "UK", "12345"),
                List.of("yoga"), List.of("cooking"), List.of("jazz"));

        LoginResponse response = customerService.register(req);

        assertThat(response.token()).isEqualTo("token123");
        assertThat(response.email()).isEqualTo("jane@example.com");

        verify(passwordEncoder).encode("secret123");
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(customerCaptor.getValue().getAddress().getCity()).isEqualTo("London");

        verify(preferencesRepository).save(any(CustomerPreferences.class));
    }

    @Test
    void register_nullAddress_doesNotThrowAndLeavesAddressNull() {
        customerService = newService();
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer());
        when(jwtUtil.generateToken(anyString())).thenReturn("token123");

        RegisterRequest req = registerRequest(null, List.of(), List.of(), List.of());

        customerService.register(req);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getAddress()).isNull();
    }

    @Test
    void register_nullPreferenceLists_defaultToEmptyLists() {
        customerService = newService();
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer());
        when(jwtUtil.generateToken(anyString())).thenReturn("token123");

        RegisterRequest req = registerRequest(null, null, null, null);

        customerService.register(req);

        ArgumentCaptor<CustomerPreferences> prefsCaptor = ArgumentCaptor.forClass(CustomerPreferences.class);
        verify(preferencesRepository).save(prefsCaptor.capture());
        assertThat(prefsCaptor.getValue().getSports()).isEmpty();
        assertThat(prefsCaptor.getValue().getHobbies()).isEmpty();
        assertThat(prefsCaptor.getValue().getInterests()).isEmpty();
    }

    @Test
    void register_duplicateEmail_throwsAndNeverSaves() {
        customerService = newService();
        when(customerRepository.existsByEmail("jane@example.com")).thenReturn(true);

        RegisterRequest req = registerRequest(null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> customerService.register(req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(customerRepository, never()).save(any());
        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void login_happyPath_returnsToken() {
        customerService = newService();
        Customer customer = savedCustomer();
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("jane@example.com")).thenReturn("token123");

        LoginResponse response = customerService.login(new LoginRequest("jane@example.com", "secret123"));

        assertThat(response.token()).isEqualTo("token123");
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        customerService = newService();
        when(customerRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.login(new LoginRequest("unknown@example.com", "secret123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        customerService = newService();
        Customer customer = savedCustomer();
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> customerService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void getProfile_withPreferences_returnsPopulatedDto() {
        customerService = newService();
        Customer customer = savedCustomer();
        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setSports(List.of("yoga"));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(prefs));

        CustomerProfileDto dto = customerService.getProfile("jane@example.com");

        assertThat(dto.sports()).containsExactly("yoga");
    }

    @Test
    void getProfile_withoutPreferences_returnsDefaultedDto() {
        customerService = newService();
        Customer customer = savedCustomer();
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());

        CustomerProfileDto dto = customerService.getProfile("jane@example.com");

        assertThat(dto.sports()).isEmpty();
        assertThat(dto.likesTraveling()).isFalse();
    }

    @Test
    void updateProfile_existingPreferences_updatesInPlace() {
        customerService = newService();
        Customer customer = savedCustomer();
        CustomerPreferences existingPrefs = new CustomerPreferences();
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.of(existingPrefs));

        CustomerProfileDto dto = new CustomerProfileDto(
                1L, "jane@example.com", "Jane", "Doe", null, 31, null, "MSc", "Engineer",
                List.of("cycling"), List.of(), List.of(), true, true, "updated notes",
                true, List.of(SuggestionCategory.DAILY));

        customerService.updateProfile("jane@example.com", dto);

        ArgumentCaptor<CustomerPreferences> prefsCaptor = ArgumentCaptor.forClass(CustomerPreferences.class);
        verify(preferencesRepository).save(prefsCaptor.capture());
        assertThat(prefsCaptor.getValue()).isSameAs(existingPrefs);
        assertThat(prefsCaptor.getValue().getSports()).containsExactly("cycling");
        assertThat(prefsCaptor.getValue().isVacationMode()).isTrue();
        assertThat(prefsCaptor.getValue().getPausedCategories()).containsExactly(SuggestionCategory.DAILY);
    }

    @Test
    void updateProfile_noExistingPreferences_createsNewLinkedToCustomer() {
        customerService = newService();
        Customer customer = savedCustomer();
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(preferencesRepository.findByCustomer(customer)).thenReturn(Optional.empty());

        CustomerProfileDto dto = new CustomerProfileDto(
                1L, "jane@example.com", "Jane", "Doe", null, 31, null, "MSc", "Engineer",
                List.of(), List.of(), List.of(), false, false, null, false, List.of());

        customerService.updateProfile("jane@example.com", dto);

        ArgumentCaptor<CustomerPreferences> prefsCaptor = ArgumentCaptor.forClass(CustomerPreferences.class);
        verify(preferencesRepository).save(prefsCaptor.capture());
        assertThat(prefsCaptor.getValue().getCustomer()).isSameAs(customer);
    }

    @Test
    void findByEmail_found_returnsCustomer() {
        customerService = newService();
        Customer customer = savedCustomer();
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));

        assertThat(customerService.findByEmail("jane@example.com")).isSameAs(customer);
    }

    @Test
    void findByEmail_notFound_throwsEntityNotFound() {
        customerService = newService();
        when(customerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findByEmail("missing@example.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
