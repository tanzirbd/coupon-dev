package com.couponplatform.userservice.security;

import com.couponplatform.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Standalone UserDetailsService implementation.
 *
 * Previously this was an anonymous bean inside SecurityConfig, which caused
 * a circular dependency:
 *   SecurityConfig → JwtAuthenticationFilter → UserDetailsService (in SecurityConfig)
 *
 * By extracting it to its own @Service, both SecurityConfig and
 * JwtAuthenticationFilter can inject it independently — no cycle.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));
    }
}