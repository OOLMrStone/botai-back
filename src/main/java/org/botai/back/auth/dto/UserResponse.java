package org.botai.back.auth.dto;

import org.botai.back.user.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String role,
        String authProvider
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getAuthProvider().name()
        );
    }
}
