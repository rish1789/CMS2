package com.cms.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the auto-generated OpenAPI spec (springdoc-openapi scans every
 * {@code @RestController} in the app; nothing here needs updating when a new endpoint is
 * added). See {@code application.yml}'s {@code springdoc.*} properties for where the UI (/docs)
 * and raw spec (/api-docs) are served.
 */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Clinic Management System API",
                        version = "0.0.1",
                        description =
                                "Multi-tenant clinic management: patient booking/waitlist, clinic staff "
                                        + "operations (scheduling, queues, walk-ins, clinical documentation), and "
                                        + "Super Admin clinic/doctor verification. Endpoints are grouped by path "
                                        + "prefix: /api/v1/patients/** (patient-authenticated), /api/v1/clinics/** "
                                        + "(staff-authenticated, clinic-scoped), /api/v1/staff/** (staff login), "
                                        + "/api/v1/admin/** (Super Admin), /api/v1/discovery/** and "
                                        + "/api/v1/doctors/** (public).",
                        license = @License(name = "MIT")))
public class OpenApiConfig {}
