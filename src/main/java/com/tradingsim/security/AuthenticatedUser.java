package com.tradingsim.security;

public record AuthenticatedUser(
        Long id,
        String email,
        String displayName) {
}
