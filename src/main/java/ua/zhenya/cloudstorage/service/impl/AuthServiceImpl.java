package ua.zhenya.cloudstorage.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.zhenya.cloudstorage.config.UserDetailsImpl;
import ua.zhenya.cloudstorage.dto.AuthRequest;
import ua.zhenya.cloudstorage.dto.AuthResponse;
import ua.zhenya.cloudstorage.event.UserRegisteredEvent;
import ua.zhenya.cloudstorage.exception.CloudStorageException;
import ua.zhenya.cloudstorage.mapper.UserMapper;
import ua.zhenya.cloudstorage.model.User;
import ua.zhenya.cloudstorage.repository.UserRepository;
import ua.zhenya.cloudstorage.service.AuthService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AuthResponse signUp(AuthRequest authRequest, HttpServletRequest request) {
        checkAlreadyAuthenticated();
        if (userRepository.findByUsernameIgnoreCase(authRequest.getUsername()).isPresent())
            throw new CloudStorageException("User with this username already exists!", HttpStatus.CONFLICT);

        return Optional.of(authRequest)
                .map(userMapper::toEntity)
                .map(user -> {
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                    User savedUser = userRepository.save(user);
                    AuthResponse authResponse = authenticateAndCreateSession(savedUser.getUsername(), authRequest.getPassword(), request);
                    eventPublisher.publishEvent(new UserRegisteredEvent(this, savedUser));
                    return authResponse;
                })
                .orElseThrow(() -> new CloudStorageException("Error creating user: " + authRequest.getUsername(),
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    @Transactional
    public AuthResponse signIn(AuthRequest request, HttpServletRequest httpServletRequest) {
        checkAlreadyAuthenticated();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            return createSession(authentication, httpServletRequest);
        } catch (AuthenticationException e) {
            throw new CloudStorageException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    @Transactional
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public AuthResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            UserDetailsImpl userDetailsImpl = (UserDetailsImpl) authentication.getPrincipal();
            return new AuthResponse(userDetailsImpl.getUsername());
        }
        throw new CloudStorageException("No authenticated user found!", HttpStatus.UNAUTHORIZED);
    }

    private AuthResponse authenticateAndCreateSession(String username, String rawPassword, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword));
        return createSession(authentication, request);
    }

    private AuthResponse createSession(Authentication authentication, HttpServletRequest request) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return new AuthResponse(userDetails.getUsername());
    }

    private void checkAlreadyAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            throw new CloudStorageException("User is already authenticated. Cannot authenticate again!", HttpStatus.FORBIDDEN);
        }
    }
}