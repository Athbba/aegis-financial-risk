package com.aegis.auth;

import com.aegis.shared.DomainException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public UUID id(Authentication auth) {
    return users.findByEmail(auth.getName()).orElseThrow(DomainException::missing).getId();
  }
}
