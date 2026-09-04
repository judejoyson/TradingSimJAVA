package com.tradingsim.persistence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JournalRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 5000) String notes) {
}
