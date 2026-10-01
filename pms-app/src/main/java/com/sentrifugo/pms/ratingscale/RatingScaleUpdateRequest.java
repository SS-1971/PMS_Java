package com.sentrifugo.pms.ratingscale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RatingScaleUpdateRequest(
        @NotEmpty @Valid List<LevelUpdate> levels,
        boolean isDefault,
        boolean showDefinitionsToEmployees) {

    public record LevelUpdate(
            @NotNull int rating,
            @NotBlank @Size(max = 40) String label,
            @Size(max = 200) String definition,
            @NotNull @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal scoreMin,
            @NotNull @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal scoreMax,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {
    }
}
