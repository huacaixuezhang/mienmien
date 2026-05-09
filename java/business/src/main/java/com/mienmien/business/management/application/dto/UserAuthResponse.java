package com.mienmien.business.management.application.dto;

public record UserAuthResponse(String userId, String phone, String message, String sessionToken) {
}
