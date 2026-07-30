package com.codecraft.eventsuggestion.security;

import com.codecraft.eventsuggestion.domain.Customer;
import com.codecraft.eventsuggestion.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Test
    void loadUserByUsername_found_returnsUserWithRoleUser() {
        Customer customer = new Customer();
        customer.setEmail("jane@example.com");
        customer.setPassword("hashed-password");
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(customerRepository);
        UserDetails userDetails = service.loadUserByUsername("jane@example.com");

        assertThat(userDetails.getUsername()).isEqualTo("jane@example.com");
        assertThat(userDetails.getPassword()).isEqualTo("hashed-password");
        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFoundException() {
        when(customerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(customerRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
