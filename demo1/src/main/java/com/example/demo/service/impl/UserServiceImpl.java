package com.example.demo.service.impl;

import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import com.example.demo.exceptions.NotFoundException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserDTO> getAll() {
        return userRepository.findAll().stream().map(UserMapper::toDTO).toList();
    }

    @Override
    public UserDTO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User không tồn tại"));
        return UserMapper.toDTO(user);
    }

    @Override
    public UserDTO create(UserDTO dto) {
        if (dto == null) throw new IllegalArgumentException("DTO không được null");
        if (dto.getEmail() == null || dto.getEmail().isBlank()) throw new IllegalArgumentException("Email không hợp lệ");
        if (dto.getName() == null || dto.getName().isBlank()) throw new IllegalArgumentException("Name không hợp lệ");

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalStateException("Email đã tồn tại");
        }

        User saved = userRepository.save(UserMapper.toEntity(dto));
        return UserMapper.toDTO(saved);
    }

    @Override
    public UserDTO update(Long id, UserDTO dto) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User không tồn tại"));

        if (dto.getEmail() != null && !dto.getEmail().isBlank() && !dto.getEmail().equals(existing.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalStateException("Email đã tồn tại");
            }
            existing.setEmail(dto.getEmail());
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            existing.setName(dto.getName());
        }

        return UserMapper.toDTO(userRepository.save(existing));
    }

    @Override
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User không tồn tại");
        }
        userRepository.deleteById(id);
    }
}

