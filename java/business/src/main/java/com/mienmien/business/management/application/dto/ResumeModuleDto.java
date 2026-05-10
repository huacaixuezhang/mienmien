package com.mienmien.business.management.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ResumeModuleDto(@NotBlank String id, String title, String text) {
}
