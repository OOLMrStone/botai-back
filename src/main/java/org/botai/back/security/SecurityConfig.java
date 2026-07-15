package org.botai.back.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF: double-submit pattern for the SPA. The token travels in the
                // XSRF-TOKEN cookie (readable by the frontend), which sends it back
                // in the X-XSRF-TOKEN header on every mutating request.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/csrf").permitAll()
                        .anyRequest().authenticated())
                // Session fixation protection: the session id rotates on login
                // (see AuthService), so a pre-login cookie can never be promoted
                // to an authenticated one.
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
                // This is a JSON API: unauthenticated requests get a 401, never a
                // redirect to a login page.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        // ---------------------------------------------------------------------
        // External login (Google etc.) — enable together with the
        // spring.security.oauth2.client.registration section in application.yaml:
        //
        // http.oauth2Login(oauth -> oauth
        //         .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
        //         .defaultSuccessUrl("/", true));
        //
        // The users table already supports it: auth_provider + provider_id columns,
        // nullable password_hash. The custom OIDC user service should find-or-create
        // a User row by (provider, providerId).
        // ---------------------------------------------------------------------

        return http.build();
    }

    /**
     * BCrypt by default (via the delegating encoder, so the algorithm can be
     * upgraded later without breaking existing hashes). BCrypt generates and
     * embeds a random per-password salt in the hash itself.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Persists the authenticated SecurityContext into the HTTP session, which
     * Spring Session stores in Postgres.
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
