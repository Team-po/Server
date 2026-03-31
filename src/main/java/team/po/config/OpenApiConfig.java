package team.po.config;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;

@Configuration
@SecurityScheme(
	name = "bearerAuth",
	type = SecuritySchemeType.HTTP,
	scheme = "bearer",
	bearerFormat = "JWT",
	in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		io.swagger.v3.oas.models.security.SecurityScheme bearerScheme =
			new io.swagger.v3.oas.models.security.SecurityScheme()
				.type(Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")
				.in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
				.name("Authorization");

		SecurityRequirement requirement = new SecurityRequirement().addList("bearerAuth");

		return new OpenAPI()
			.components(new Components().addSecuritySchemes("bearerAuth", bearerScheme))
			.security(Collections.singletonList(requirement));
	}
}
