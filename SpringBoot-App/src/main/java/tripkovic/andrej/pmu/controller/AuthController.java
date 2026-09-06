package tripkovic.andrej.pmu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tripkovic.andrej.pmu.dto.AuthResponse;
import tripkovic.andrej.pmu.dto.LoginRequest;
import tripkovic.andrej.pmu.dto.RegisterRequest;
import tripkovic.andrej.pmu.dto.UpdateProfileRequest;
import tripkovic.andrej.pmu.model.User;
import tripkovic.andrej.pmu.repository.UserRepository;
import tripkovic.andrej.pmu.security.JwtUtil;
import tripkovic.andrej.pmu.security.UserDetailsServiceImpl;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        if (request.getDisplayUsername() != null && !request.getDisplayUsername().isBlank()) {
            user.setDisplayUsername(request.getDisplayUsername().trim());
        }
        if (request.getPhotoBase64() != null && !request.getPhotoBase64().isBlank()) {
            user.setPhotoBase64(request.getPhotoBase64());
        }
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getUsername(), user.getDisplayUsername(), user.getName(), user.getEmail(), user.getRole(), user.getPhotoBase64()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account is disabled");
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getDisplayUsername(), user.getName(), user.getEmail(), user.getRole(), user.getPhotoBase64()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();

        if (request.getDisplayUsername() != null) {
            String trimmed = request.getDisplayUsername().trim();
            user.setDisplayUsername(trimmed.isEmpty() ? null : trimmed);
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null ||
                    !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        if (request.getPhotoBase64() != null) {
            user.setPhotoBase64(request.getPhotoBase64().isBlank() ? null : request.getPhotoBase64());
        }

        userRepository.save(user);

        UserDetails updatedDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String newToken = jwtUtil.generateToken(updatedDetails);
        return ResponseEntity.ok(new AuthResponse(newToken, user.getUsername(), user.getDisplayUsername(), user.getName(), user.getEmail(), user.getRole(), user.getPhotoBase64()));
    }
}
