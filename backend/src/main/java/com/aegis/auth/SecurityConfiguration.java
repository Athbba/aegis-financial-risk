package com.aegis.auth;

import com.aegis.web.ApiErrors;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import tools.jackson.databind.ObjectMapper;

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
  CookieCsrfTokenRepository csrfTokens(
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
    var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Strict").path("/"));
    return repository;
  }

  @Bean
  @Order(1)
  SecurityFilterChain operations(
      HttpSecurity http,
      PasswordEncoder passwords,
      @Value("${aegis.operations.password-hash:}") String hash)
      throws Exception {
    if (!hash.isEmpty() && !hash.matches("\\$2[aby]\\$\\d{2}\\$.{53}"))
      throw new IllegalStateException("Operations password must be a BCrypt hash");
    var identities =
        new InMemoryUserDetailsManager(
            hash.isEmpty()
                ? List.of()
                : List.of(User.withUsername("operator").password(hash).roles("OPS").build()));
    var provider = new DaoAuthenticationProvider(identities);
    provider.setPasswordEncoder(passwords);
    http.securityMatcher("/actuator/**")
        .authenticationManager(new ProviderManager(provider))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .hasRole("OPS"))
        .httpBasic(org.springframework.security.config.Customizer.withDefaults())
        .headers(h -> h.addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer")));
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain security(
      HttpSecurity http,
      ObjectMapper mapper,
      SecurityContextRepository contexts,
      CookieCsrfTokenRepository csrfTokens,
      AttemptLimiter attempts,
      @Value("${aegis.auth.login-client-limit:30}") int loginLimit,
      @Value("${aegis.auth.registration-client-limit:30}") int registrationLimit,
      @Value("${aegis.trusted-proxy:}") String trustedPeer)
      throws Exception {
    http.securityContext(c -> c.securityContextRepository(contexts))
        .csrf(
            c ->
                c.csrfTokenRepository(csrfTokens)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterAfter(
            new AuthenticationAdmission(
                attempts, mapper, loginLimit, registrationLimit, trustedPeer),
            CsrfFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/error")
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
                        c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer"))
                    .addHeaderWriter(
                        new StaticHeadersWriter(
                            "Permissions-Policy", "camera=(), microphone=(), geolocation=()")));
    return http.build();
  }
}
