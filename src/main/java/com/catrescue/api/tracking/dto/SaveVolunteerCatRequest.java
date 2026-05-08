package com.catrescue.api.tracking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveVolunteerCatRequest(
        @NotNull @Positive Long catId
) {
}
