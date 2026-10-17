package com.aegis.web;

import java.math.BigDecimal;
import org.springframework.context.annotation.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Configuration
public class WebConfiguration {
  @Bean
  SimpleModule exactDecimals() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
    return module;
  }

  @Bean
  @Scope("prototype")
  tools.jackson.databind.json.JsonMapper.Builder jsonMapperBuilder(
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer>
          customizers) {
    var constraints =
        tools.jackson.core.StreamReadConstraints.builder()
            .maxNestingDepth(20)
            .maxStringLength(8192)
            .maxNumberLength(100)
            .build();
    var factory =
        tools.jackson.core.json.JsonFactory.builder().streamReadConstraints(constraints).build();
    var builder = tools.jackson.databind.json.JsonMapper.builder(factory);
    customizers.orderedStream().forEach(c -> c.customize(builder));
    return builder;
  }
}
