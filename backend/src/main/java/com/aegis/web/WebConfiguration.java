package com.aegis.web;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import org.springframework.context.annotation.*;

@Configuration
public class WebConfiguration {
  @Bean
  SimpleModule exactDecimals() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
    return module;
  }
}
