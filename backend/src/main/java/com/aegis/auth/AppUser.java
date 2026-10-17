package com.aegis.auth;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
  @Id private UUID id;

  @Column(name = "email")
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "created_at")
  private Instant createdAt;

  protected AppUser() {}

  public AppUser(UUID id, String email, String passwordHash, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
