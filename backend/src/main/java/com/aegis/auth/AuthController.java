package com.aegis.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService accounts;
  private final SecurityContextRepository contexts;

  public AuthController(AuthService accounts, SecurityContextRepository contexts) {
    this.accounts = accounts;
    this.contexts = contexts;
  }

  public record Credentials(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  AuthService.Identity register(
      @Valid @RequestBody Credentials input, HttpServletRequest req, HttpServletResponse res) {
    var user = accounts.register(input.email(), input.password());
    establish(accounts.authenticate(user.email(), input.password()), req, res);
    return user;
  }

  @PostMapping("/login")
  AuthService.Identity login(
      @Valid @RequestBody Credentials input, HttpServletRequest req, HttpServletResponse res) {
    var auth = accounts.authenticate(input.email(), input.password());
    establish(auth, req, res);
    return accounts.identity(auth.getName());
  }

  @GetMapping("/me")
  AuthService.Identity me(Authentication auth) {
    return accounts.identity(auth.getName());
  }

  private void establish(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    if (req.getSession(false) != null) req.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    CookieCsrfTokenRepository.withHttpOnlyFalse().saveToken(null, req, res);
  }
}
