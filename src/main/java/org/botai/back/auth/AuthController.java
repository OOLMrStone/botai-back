package org.botai.back.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.botai.back.auth.dto.LoginRequest;
import org.botai.back.auth.dto.RegisterRequest;
import org.botai.back.auth.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * CSRF bootstrap for the SPA: calling this endpoint sets the XSRF-TOKEN
     * cookie (and returns the token) so subsequent POSTs can pass it back in
     * the X-XSRF-TOKEN header.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest servletRequest,
                              HttpServletResponse servletResponse) {
        return UserResponse.from(authService.login(request, servletRequest, servletResponse));
    }

    /** Who am I — lets the frontend restore UI state from the session cookie. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(authService.currentUser(authentication));
    }

    // POST /api/auth/logout is handled by Spring Security's logout filter
    // (see SecurityConfig): invalidates the session and deletes the cookie.
}
