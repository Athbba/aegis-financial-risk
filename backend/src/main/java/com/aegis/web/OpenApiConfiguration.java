package com.aegis.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "AEGIS simulated investing API",
            version = "0.1.0",
            description =
                "Educational simulation only. All default assets and prices are synthetic. USD"
                    + " only."),
    security = @SecurityRequirement(name = "sessionCookie"))
@SecurityScheme(
    name = "sessionCookie",
    type = SecuritySchemeType.APIKEY,
    in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.COOKIE,
    paramName = "JSESSIONID")
public class OpenApiConfiguration {
  static {
    SpringDocUtils.getConfig()
        .replaceWithSchema(
            BigDecimal.class,
            new StringSchema()
                .pattern("^-?[0-9]+(\\.[0-9]+)?$")
                .description("Exact decimal string; amounts are USD unless specified."));
  }

  @org.springframework.context.annotation.Bean
  org.springdoc.core.customizers.OpenApiCustomizer publicAuthenticationOperations() {
    return api ->
        java.util.List.of("/api/auth/csrf", "/api/auth/register", "/api/auth/login")
            .forEach(
                path -> {
                  var item = api.getPaths().get(path);
                  if (item != null)
                    item.readOperations()
                        .forEach(operation -> operation.setSecurity(java.util.List.of()));
                });
  }
}
