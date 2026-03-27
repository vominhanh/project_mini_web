package com.example.demo.controller.dto;

public record RegisterRequest(
        String email,
        String name,
        String password
) {
}
