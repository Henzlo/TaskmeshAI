package com.taskmesh.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskMeshOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("TaskMesh AI API")
                .version("1.0.0")
                .description("Distributed multi-agent legal document analyzer — Facts, Law, and Risk agents orchestrated with shared memory"));
    }
}
