package com.example.demo.controller.dto;

public class ReportUserRow {
    private final Long id;
    private final String firstname;
    private final String lastname;
    private final String email;
    private final String username;
    private final String role;

    public ReportUserRow(Long id, String firstname, String lastname, String email, String username, String role) {
        this.id = id;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.username = username;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
