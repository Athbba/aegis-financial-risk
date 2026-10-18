package com.aegis.auth;

import com.aegis.web.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Installed inside the security chain after CSRF, never independently registered as a servlet
 * filter.
 */
public class AuthenticationAdmission extends OncePerRequestFilter {
  private final AttemptLimiter limiter;
  private final ObjectMapper mapper;
  private final int loginLimit, registrationLimit;
  private final String trustedPeer;

  public AuthenticationAdmission(
      AttemptLimiter limiter,
      ObjectMapper mapper,
      int loginLimit,
      int registrationLimit,
      String trustedPeer) {
    this.trustedPeer = trustedPeer;
    this.limiter = limiter;
    this.mapper = mapper;
    this.loginLimit = loginLimit;
    this.registrationLimit = registrationLimit;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    if (req.getMethod().equals("POST")
        && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
      try {
        boolean login = path.endsWith("/login");
        // The isolated edge replaces this private header; all other peers use their transport
        // address.
        limiter.acquire(
            login ? "login-client" : "register-client",
            ClientAddress.resolve(req, trustedPeer),
            login ? loginLimit : registrationLimit,
            Duration.ofSeconds(login ? 300 : 3600));
      } catch (IllegalArgumentException e) {
        res.sendError(400);
        return;
      } catch (AttemptLimiter.Limited e) {
        res.setStatus(429);
        res.setHeader("Retry-After", Long.toString(e.retryAfter()));
        res.setContentType("application/problem+json");
        mapper.writeValue(
            res.getOutputStream(),
            ApiErrors.problem(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", e.getMessage()));
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
