package com.cms.identity.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterClinicRequest(@Valid @NotNull ClinicDto clinic, @Valid @NotNull AdminDto admin) {

    public record ClinicDto(
            @NotBlank String name,
            @NotBlank String address,
            // patient-search-advanced-filtering: optional, like contactEmail/contactMobile - a
            // registration is never blocked for omitting it, but capturing it up front means new
            // clinics are city-filterable in public discovery search without a separate edit step.
            String city,
            @Email String contactEmail,
            String contactMobile) {}

    public record AdminDto(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String password,
            String mobile) {}
}
