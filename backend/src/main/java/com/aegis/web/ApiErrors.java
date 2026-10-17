package com.aegis.web;

import com.aegis.shared.DomainException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  public static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(code);
    p.setProperty("code", code);
    return p;
  }

  @ExceptionHandler(DomainException.class)
  ProblemDetail domain(DomainException e) {
    HttpStatus status =
        switch (e.kind()) {
          case INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case CONFLICT -> HttpStatus.CONFLICT;
        };
    return problem(status, e.code(), e.getMessage());
  }

  @ExceptionHandler({
    BindException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ProblemDetail validation(Exception e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_INPUT",
        "Check field formats, required values, and allowed ranges.");
  }

  @ExceptionHandler(BadCredentialsException.class)
  ProblemDetail credentials() {
    return problem(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict(DataIntegrityViolationException e) {
    return problem(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception e) {
    if (e instanceof org.springframework.web.ErrorResponse error) {
      HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
      return problem(status, status.name(), error.getBody().getDetail());
    }
    log.error("Unhandled request failure", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.");
  }
}
