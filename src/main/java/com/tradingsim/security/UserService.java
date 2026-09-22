package com.tradingsim.security;

import com.tradingsim.web.BadRequestException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tradingsim.persistence.PortfolioProvisioningService;

import java.util.Locale;

@Service
public class UserService implements UserDetailsService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PortfolioProvisioningService portfolios;

    public UserService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            PortfolioProvisioningService portfolios) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.portfolios = portfolios;
    }

    @Transactional
    public AuthenticatedUser register(RegistrationRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new BadRequestException("An account already exists for that email address.");
        }
        try {
            AppUser user = users.saveAndFlush(new AppUser(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.displayName().trim()));
            portfolios.createFor(user);
            return view(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("An account already exists for that email address.");
        }
    }

    @Transactional(readOnly = true)
    public AppUser require(String email) {
        return users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
    }

    @Transactional
    public AppUser requireForUpdate(String email) {
        return users.findForUpdateByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = require(username);
        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }

    public AuthenticatedUser view(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.US);
    }
}
