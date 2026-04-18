package com.chatapp.service;

import com.chatapp.dto.SignInRequest;
import com.chatapp.dto.SignUpRequest;
import com.chatapp.entity.User;
import com.chatapp.exception.InvalidCredentialsException;
import com.chatapp.exception.UserAlreadyExistsException;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());

        return userRepository.save(user);
    }

    public User signIn(SignInRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return user;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }

}
