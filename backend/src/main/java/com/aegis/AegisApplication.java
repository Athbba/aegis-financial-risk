package com.aegis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AegisApplication {
  public static void main(String[] args) {
    if (java.util.Arrays.asList(args).contains("--migrate-only")) {
      com.aegis.operations.DatabaseMaintenance.migrate(System.getenv());
      return;
    }
    SpringApplication.run(AegisApplication.class, args);
  }
}
