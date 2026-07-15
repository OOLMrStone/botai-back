package org.botai.back.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.botai.back.auth.dto.LoginRequest;
import org.botai.back.auth.dto.RegisterRequest;
import org.botai.back.user.User;
import org.botai.back.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .build();
        return userRepository.save(user);
    }

    /**
     * Verifies credentials and establishes the session: the SecurityContext is
     * written to the HTTP session, which Spring Session persists in Postgres.
     * The browser only ever holds the opaque SESSION cookie.
     */
    public User login(LoginRequest request, HttpServletRequest servletRequest,
                      HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().trim().toLowerCase(Locale.ROOT), request.password()));

        // Rotate the session id so any session established before login cannot be
        // reused by an attacker who planted it (session fixation).
        if (servletRequest.getSession(false) != null) {
            servletRequest.changeSessionId();
        }

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return currentUser(authentication);
    }

    @Transactional(readOnly = true)
    public User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found: " + authentication.getName()));
    }
}
