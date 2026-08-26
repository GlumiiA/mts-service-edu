package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.auth.XmlUserStore;
import ru.aigul.mts_service.dto.UserCreateRequest;
import ru.aigul.mts_service.exception.UserAlreadyExists;
import ru.aigul.mts_service.model.Role;
import ru.aigul.mts_service.model.User;
import ru.aigul.mts_service.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final XmlUserStore xmlUserStore;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public User createUser(UserCreateRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new UserAlreadyExists(req.getEmail());
        }

        User u = new User();
        u.setEmail(req.getEmail());
        u.setName(req.getName());
        String hash = passwordEncoder.encode(req.getPassword());
        u.setPasswordHash(hash);
        u.setRole(Role.USER);

        User saved = userRepository.save(u);

        try {
            xmlUserStore.addUser(saved.getEmail(), hash, saved.getRole().name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist user to XML store", e);
        }

        return saved;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}