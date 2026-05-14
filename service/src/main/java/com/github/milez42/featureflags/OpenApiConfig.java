package com.github.milez42.featureflags;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI featureFlagOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Feature Flag API")
                .version("0.1.0")
                .description("Feature flag management, evaluation, and audit APIs."));
  }

  @Bean
  OpenApiCustomizer validationConstraintsOpenApiCustomizer() {
    return openApi -> {
      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem ->
                  pathItem
                      .readOperations()
                      .forEach(
                          operation -> {
                            if (operation.getParameters() == null) {
                              return;
                            }
                            operation.getParameters().stream()
                                .filter(OpenApiConfig::isFlagKeyPathParameter)
                                .map(Parameter::getSchema)
                                .forEach(OpenApiConfig::setMinLengthOne);
                          }));

      @SuppressWarnings({"rawtypes", "unchecked"})
      Map<String, Schema<?>> schemas = (Map) openApi.getComponents().getSchemas();
      setPropertyMinLength(schemas, "CreateFeatureFlagRequest", "flagKey");
      setArrayItemMinLength(schemas, "CreateFeatureFlagRequest", "tenantAllowlist");
      setPropertyMinLength(schemas, "EvaluationPreviewContext", "environment");
      setArrayItemMinLength(schemas, "ProposedFeatureFlagChange", "tenantAllowlist");
      setPropertyMinLength(schemas, "EvaluateFeatureFlagRequest", "flagKey");
      setPropertyMinLength(schemas, "EvaluateFeatureFlagRequest", "environment");
      setArrayItemMinLength(schemas, "UpdateFeatureFlagRequest", "tenantAllowlist");
    };
  }

  private static boolean isFlagKeyPathParameter(Parameter parameter) {
    return "path".equals(parameter.getIn()) && "flagKey".equals(parameter.getName());
  }

  private static void setPropertyMinLength(
      Map<String, Schema<?>> schemas, String schemaName, String propertyName) {
    Schema<?> schema = schemas.get(schemaName);
    if (schema == null || schema.getProperties() == null) {
      return;
    }
    setMinLengthOne((Schema<?>) schema.getProperties().get(propertyName));
  }

  private static void setArrayItemMinLength(
      Map<String, Schema<?>> schemas, String schemaName, String propertyName) {
    Schema<?> schema = schemas.get(schemaName);
    if (schema == null || schema.getProperties() == null) {
      return;
    }
    Schema<?> property = (Schema<?>) schema.getProperties().get(propertyName);
    if (property == null) {
      return;
    }
    setMinLengthOne(property.getItems());
  }

  private static void setMinLengthOne(Schema<?> schema) {
    if (schema != null) {
      schema.setMinLength(1);
    }
  }
}
