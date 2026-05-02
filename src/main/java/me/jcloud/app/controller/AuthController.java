package me.jcloud.app.controller;

import jakarta.validation.Valid;
import me.jcloud.app.dto.LoginRequest;
import me.jcloud.app.dto.RegisterRequest;
import me.jcloud.app.model.User;
import me.jcloud.app.security.JwtService;
import me.jcloud.app.security.TokenSessionService;
import me.jcloud.app.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JwtService jwtService;
    private final TokenSessionService sessionService;
    private final UserService userService;

    public AuthController(JwtService jwtService, TokenSessionService sessionService, UserService userService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );
        
        String token = jwtService.createToken(user.getId());
        sessionService.activateSession(token, user.getId(), TokenSessionService.SESSION_TTL);
        return Map.of("token", token);
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(
                request.getEmail(),
                request.getPassword()
        );
        
        String token = jwtService.createToken(user.getId());
        sessionService.activateSession(token, user.getId(), TokenSessionService.SESSION_TTL);

        return Map.of("token", token);
    }
}
