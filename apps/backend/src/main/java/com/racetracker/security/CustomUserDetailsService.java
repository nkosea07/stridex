package com.racetracker.security;

import com.racetracker.model.User;
import com.racetracker.repository.UserCapabilityRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserCapabilityRepository userCapabilityRepository;

    public CustomUserDetailsService(UserRepository userRepository, UserCapabilityRepository userCapabilityRepository) {
        this.userRepository = userRepository;
        this.userCapabilityRepository = userCapabilityRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        userCapabilityRepository.findByUserIdAndActiveTrueOrderByCapability(user.getId())
            .forEach(capability -> authorities.add(new SimpleGrantedAuthority(
                "CAP_" + capability.getCapability().name())));

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash(),
            Boolean.TRUE.equals(user.getIsActive()) && Boolean.TRUE.equals(user.getEmailVerified()),
            true,
            true,
            true,
            authorities
        );
    }
}
