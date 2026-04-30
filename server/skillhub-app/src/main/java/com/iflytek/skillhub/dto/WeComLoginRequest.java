package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record WeComLoginRequest(
        @NotBlank(message = "validation.auth.wecom.code.notBlank")
        String code
) {
}
