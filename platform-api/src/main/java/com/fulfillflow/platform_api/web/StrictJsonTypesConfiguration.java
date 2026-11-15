package com.fulfillflow.platform_api.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Strict JSON types for text fields across the whole API (spec, "Kontrakt API"):
 * a number or boolean sent for a {@code String} field (e.g. {@code "name": 123},
 * {@code "sku": 123}, {@code "description": true}) is a 400, never silently
 * stringified to {@code "123"} / {@code "true"}.
 *
 * <p>This mirrors the strict numeric handling already in {@code
 * application.properties} ({@code accept-float-as-int=false},
 * {@code allow-coercion-of-scalars=false}), which does not cover the
 * number/boolean&nbsp;&rarr;&nbsp;{@code String} direction. The rule is set once,
 * globally, by customizing Boot's auto-configured Jackson 3 {@code JsonMapper}
 * (so {@code spring.jackson.*} keeps working) rather than per DTO.
 */
@Configuration(proxyBeanMethods = false)
class StrictJsonTypesConfiguration {

	@Bean
	JsonMapperBuilderCustomizer failOnScalarToTextualCoercion() {
		return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> {
			config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
			config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
			config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
		});
	}
}
