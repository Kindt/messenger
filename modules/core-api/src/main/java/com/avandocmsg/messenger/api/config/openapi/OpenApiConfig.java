package com.avandocmsg.messenger.api.config.openapi;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import java.util.List;
import java.util.Set;

public class OpenApiConfig {

    public static OpenApiResource create(String version) {
        var openAPI = new OpenAPI()
            .info(new Info()
                .title("AvandocMsg.Messenger API")
                .version(version)
                .description("REST API for AvandocMsg.Messenger — чаты, контакты, сообщения, медиа")
                .contact(new Contact()
                    .name("AvandocMsg Team")
                    .email("dev@avandocmsg.com"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://avandocmsg.com/license")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local dev"),
                new Server().url("https://api.avandocmsg.com").description("Production")
            ))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Keycloak access_token")));

        var swaggerConfig = new SwaggerConfiguration()
            .openAPI(openAPI)
            .resourcePackages(Set.of("com.avandocmsg.messenger.api"))
            .prettyPrint(true);

        var openApiResource = new OpenApiResource();
        openApiResource.setOpenApiConfiguration(swaggerConfig);
        return openApiResource;
    }
}
