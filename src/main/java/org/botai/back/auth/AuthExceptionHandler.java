package org.botai.back.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailTaken(EmailAlreadyUsedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Covers BadCredentialsException, DisabledException, etc. from the login
     * endpoint. The message is deliberately generic: revealing whether the email
     * exists would enable account enumeration.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
