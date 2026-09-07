package com.eventzone.dto.ticketcategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TicketCategoryRequest(
        @NotBlank(message = "Name is required") String name,
        @NotNull(message = "Price is required") @DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative") BigDecimal price,
        @Min(value = 1, message = "Total seats must be at least 1") int totalSeats
) {
}
