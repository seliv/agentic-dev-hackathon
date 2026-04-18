package com.chatapp.controller;

import com.chatapp.dto.SignUpRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = userService.signUp(request);
        UserResponse response = UserResponse.fromEntity(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
