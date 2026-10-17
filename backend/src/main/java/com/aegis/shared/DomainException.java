package com.aegis.shared;

/** Business failure independent of HTTP. The web boundary maps its kind to a status. */
public class DomainException extends RuntimeException {
  public enum Kind {
    INVALID,
    NOT_FOUND,
    CONFLICT
  }

  private final String code;
  private final Kind kind;

  public DomainException(String code, String detail, Kind kind) {
    super(detail);
    this.code = code;
    this.kind = kind;
  }

  public String code() {
    return code;
  }

  public Kind kind() {
    return kind;
  }

  public static DomainException invalid(String code, String detail) {
    return new DomainException(code, detail, Kind.INVALID);
  }

  public static DomainException missing() {
    return new DomainException("NOT_FOUND", "Resource not found.", Kind.NOT_FOUND);
  }
}
