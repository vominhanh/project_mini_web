package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody UpsertUserRequest request) {
        String email = getEmail(request);
        String name = getName(request);

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email da ton tai");
        }

        User user = new User(email, name);
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UpsertUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User khong ton tai"));

        String email = getEmail(request);
        String name = getName(request);

        userRepository.findFirstByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Email da ton tai");
                });

        user.setEmail(email);
        user.setName(name);

        User updated = userRepository.save(user);
        return toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User khong ton tai");
        }
        userRepository.deleteById(id);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                safe(user.getEmail()),
                safe(user.getName()),
                user.getCreatedAt()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        return email.trim();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ten khong hop le");
        }
        return name.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String getEmail(UpsertUserRequest request) {
        return normalizeEmail(request == null ? null : request.email());
    }

    private String getName(UpsertUserRequest request) {
        return normalizeName(request == null ? null : request.name());
    }

    public record UpsertUserRequest(String email, String name) {
    }

    public record UserResponse(Long id, String email, String name, Instant createdAt) {
    }
}

