package com.aegis.operations;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class OperationsConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
