package com.aegis.auth;

import com.aegis.web.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService users(UserRepository users) {
    return email ->
        users
            .findByEmail(email)
            .map(
                u ->
                    User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }

  @Bean
  AuthenticationManager authentication(UserDetailsService users, PasswordEncoder passwords) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(passwords);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityContextRepository contexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, ObjectMapper mapper, SecurityContextRepository contexts) throws Exception {
    http.securityContext(c -> c.securityContextRepository(contexts))
        .csrf(
            c ->
                c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/actuator/health",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/problem+json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiErrors.problem(
                                  HttpStatus.UNAUTHORIZED,
                                  "UNAUTHENTICATED",
                                  "Sign in to continue."));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/problem+json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiErrors.problem(
                                  HttpStatus.FORBIDDEN,
                                  "FORBIDDEN",
                                  "Request denied. Refresh your session and try again."));
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
    return http.build();
  }
}
