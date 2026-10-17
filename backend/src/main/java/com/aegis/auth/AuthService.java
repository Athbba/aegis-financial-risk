package com.aegis.auth;

import com.aegis.shared.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  public record Identity(UUID id, String email) {}

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final AuthenticationManager authentication;

  public AuthService(
      UserRepository users, PasswordEncoder passwords, AuthenticationManager authentication) {
    this.users = users;
    this.passwords = passwords;
    this.authentication = authentication;
  }

  @Transactional
  public Identity register(String email, String password) {
    String normalized = normalize(email);
    checkPasswordLength(password);
    if (users.findByEmail(normalized).isPresent())
      throw new DomainException(
          "EMAIL_UNAVAILABLE", "This email cannot be registered.", DomainException.Kind.CONFLICT);
    AppUser user =
        users.saveAndFlush(
            new AppUser(UUID.randomUUID(), normalized, passwords.encode(password), Instant.now()));
    return new Identity(user.getId(), user.getEmail());
  }

  public Authentication authenticate(String email, String password) {
    checkPasswordLength(password);
    return authentication.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(normalize(email), password));
  }

  public Identity identity(String email) {
    AppUser user = users.findByEmail(email).orElseThrow(DomainException::missing);
    return new Identity(user.getId(), user.getEmail());
  }

  private static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private static void checkPasswordLength(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw DomainException.invalid("PASSWORD_LENGTH", "Password must be at most 72 UTF-8 bytes.");
  }
}
