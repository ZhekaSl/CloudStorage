package ua.zhenya.cloudstorage.service.impl;


import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import ua.zhenya.cloudstorage.BaseIntegrationTest;
import ua.zhenya.cloudstorage.config.UserDetailsImpl;
import ua.zhenya.cloudstorage.dto.AuthRequest;
import ua.zhenya.cloudstorage.dto.AuthResponse;
import ua.zhenya.cloudstorage.exception.CloudStorageException;
import ua.zhenya.cloudstorage.model.User;
import ua.zhenya.cloudstorage.repository.UserRepository;
import ua.zhenya.cloudstorage.service.AuthService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class AuthServiceImplTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    public void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
    }

    @Test
    public void signUp_shouldSignUpUser() {
        AuthRequest authRequest = new AuthRequest("newuser", "password123");
        AuthResponse authResponse = authService.signUp(authRequest, request);
        assertNotNull(authResponse);
        assertEquals("newuser", authResponse.getUsername());

        Optional<User> savedUserOpt = userRepository.findByUsernameIgnoreCase("newuser");
        assertTrue(savedUserOpt.isPresent(), "User should be saved in the database");
        User savedUser = savedUserOpt.get();
        assertEquals("newuser", savedUser.getUsername());
        assertTrue(passwordEncoder.matches("password123", savedUser.getPassword()), "Password should be encoded");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals("newuser", ((UserDetailsImpl) authentication.getPrincipal()).getUsername());

        HttpSession session = request.getSession(false);
        assertNotNull(session, "Session should be created");
        SecurityContext securityContextInSession = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(securityContextInSession, "SecurityContext should be in session");
        assertEquals("newuser", ((UserDetailsImpl) securityContextInSession.getAuthentication().getPrincipal()).getUsername());
    }

    @Test
    public void signUp_throws_whenUsernameAlreadyExists() {
        String existingUsername = "existinguser";
        String existingPassword = "somepassword";

        User existingUser = new User();
        existingUser.setUsername(existingUsername);
        existingUser.setPassword(passwordEncoder.encode(existingPassword));
        try {
            userRepository.saveAndFlush(existingUser);
        } catch (DataIntegrityViolationException e) {
            assertTrue(userRepository.findByUsernameIgnoreCase(existingUsername).isPresent(),
                    "Existing user should be present even if save failed due to constraint.");
        }

        assertTrue(userRepository.findByUsernameIgnoreCase(existingUsername).isPresent(),
                "Pre-existing user should be found in DB before signUp call.");

        AuthRequest authRequest = new AuthRequest(existingUsername, "newPassword123");

        CloudStorageException exception = assertThrows(CloudStorageException.class,
                () -> authService.signUp(authRequest, request));

        assertEquals("User with this username already exists!", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());

         long userCount = userRepository.count();
         assertEquals(1, userCount, "User count should remain 1");
    }

    @Test
    public void signIn_shouldSignInCorrectly() {
        String username = "testuser";
        String rawPassword = "password123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = new User(username, encodedPassword);
        userRepository.saveAndFlush(user);
        assertTrue(userRepository.findByUsernameIgnoreCase(username).isPresent(),
                "User should exist in DB before signIn");

        AuthRequest authRequest = new AuthRequest(username, rawPassword);

        AuthResponse authResponse = authService.signIn(authRequest, request);

        assertNotNull(authResponse, "AuthResponse should not be null");
        assertEquals(username, authResponse.getUsername(), "AuthResponse should contain the correct username");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Authentication should be set in SecurityContextHolder");
        assertTrue(authentication.isAuthenticated(), "User should be authenticated");
        assertNotNull(authentication.getPrincipal(), "Principal should not be null");
        assertInstanceOf(UserDetailsImpl.class, authentication.getPrincipal(), "Principal should be UserDetailsImpl");
        assertEquals(username, ((UserDetailsImpl) authentication.getPrincipal()).getUsername(),
                "Username in SecurityContextHolder should match");

        HttpSession session = request.getSession(false);
        assertNotNull(session, "HttpSession should have been created");

        Object securityContextAttribute = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(securityContextAttribute, "SecurityContext should be stored in session");
        assertInstanceOf(SecurityContext.class, securityContextAttribute, "Session attribute should be a SecurityContext");

        SecurityContext securityContextInSession = (SecurityContext) securityContextAttribute;
        Authentication authInSession = securityContextInSession.getAuthentication();
        assertNotNull(authInSession, "Authentication should be present in session's SecurityContext");
        assertTrue(authInSession.isAuthenticated(), "Authentication in session should be authenticated");
        assertEquals(username, ((UserDetailsImpl) authInSession.getPrincipal()).getUsername(),
                "Username in session's SecurityContext should match");
    }

    @Test
    public void signOut_shouldSignOutCorrectly() {
        String username = "userToSignOut";
        String password = "password123";
        User userToSignOut = new User(username, passwordEncoder.encode(password));
        userRepository.saveAndFlush(userToSignOut);

        authService.signIn(new AuthRequest(username, password), request);

        MockHttpSession session = (MockHttpSession) request.getSession(false);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "Authentication should exist before signOut");
        assertFalse(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken,
                "Authentication should not be anonymous before signOut");
        assertNotNull(session, "Session should exist before signOut");
         assertFalse(session.isInvalid(), "Session should be valid before signOut");

        authService.signOut(request, response);

        Authentication authAfterSignOut = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authAfterSignOut, "Authentication should be null in SecurityContextHolder after signOut");

        assertNotNull(session, "Session reference should still exist");
        assertTrue(session.isInvalid(), "Session should be invalidated after signOut");
    }

    @Test
    public void signOut_doesNothing_whenNotAuthenticated() {
        Authentication initialAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(initialAuth, "Authentication should be null initially");
        HttpSession initialSession = request.getSession(false);
        assertNull(initialSession, "Session should not exist initially");

        assertDoesNotThrow(() -> {
            authService.signOut(request, response);
        }, "signOut should not throw exception when not authenticated");

        Authentication finalAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(finalAuth, "Authentication should remain null");
        HttpSession finalSession = request.getSession(false);
        assertNull(finalSession, "Session should still not exist");
    }

    @Test
    public void getCurrentUser_returnsUsername_whenUserIsAuthenticated() {
        String username = "currentuser";
        String password = "password123";
        User user = new User(username, passwordEncoder.encode(password));
        userRepository.saveAndFlush(user);

        authService.signIn(new AuthRequest(username, password), request);

        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(currentAuth, "User should be authenticated before calling getCurrentUser");
        assertEquals(username, ((UserDetailsImpl) currentAuth.getPrincipal()).getUsername());

        AuthResponse authResponse = authService.getCurrentUser();

        assertNotNull(authResponse);
        assertEquals(username, authResponse.getUsername());
    }

    @Test
    public void getCurrentUser_throws_whenNotAuthenticated() {
        CloudStorageException exception = Assertions.assertThrows(CloudStorageException.class,
                () -> authService.getCurrentUser());

        assertEquals("No authenticated user found!", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    public void signIn_throws_whenPasswordIsInvalid() {
        String username = "testuser";
        String correctPassword = "correctPassword";
        String wrongPassword = "wrongPassword";

        User user = new User(username, passwordEncoder.encode(correctPassword));
        userRepository.saveAndFlush(user);

        assertTrue(userRepository.findByUsernameIgnoreCase(username).isPresent(), "User should exist before testing invalid login");

        AuthRequest authRequest = new AuthRequest(username, wrongPassword);

        CloudStorageException exception = assertThrows(CloudStorageException.class,
                () -> authService.signIn(authRequest, request));

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Authentication should be null after failed login attempt");
        assertNull(request.getSession(false),
                "Session should not be created after failed login attempt");
    }

    @Test
    public void signIn_throws_whenUserNotFound() {
        String nonExistentUsername = "nonexistentuser";
        String anyPassword = "anyPassword";
        AuthRequest authRequest = new AuthRequest(nonExistentUsername, anyPassword);

        assertFalse(userRepository.findByUsernameIgnoreCase(nonExistentUsername).isPresent(),
                "User should not exist before testing login with non-existent user");

        CloudStorageException exception = assertThrows(CloudStorageException.class,
                () -> authService.signIn(authRequest, request));

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession(false));
    }

    @Test
    public void signUp_throws_whenUserIsAlreadyAuthenticated() {
        User user1 = new User("user1", passwordEncoder.encode("pass1"));
        userRepository.saveAndFlush(user1);

        authService.signIn(new AuthRequest("user1", "pass1"), request);

        Authentication initialAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(initialAuth, "User1 should be authenticated initially");
        assertNotNull(initialAuth.getPrincipal(), "Principal should not be null after successful login");
        assertInstanceOf(UserDetailsImpl.class, initialAuth.getPrincipal(), "Principal should be UserDetailsImpl");
        assertEquals("user1", ((UserDetailsImpl) initialAuth.getPrincipal()).getUsername());

        String newUsername = "newuser";
        String newPassword = "newPassword123";
        AuthRequest signUpRequest = new AuthRequest(newUsername, newPassword);

        CloudStorageException exception = assertThrows(CloudStorageException.class,
                () -> authService.signUp(signUpRequest, request));

        assertEquals("User is already authenticated. Cannot authenticate again!", exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    public void signIn_throws_whenUserIsAlreadyAuthenticated() {
        String user1Username = "user1";
        String user1Password = "pass1";
        userRepository.saveAndFlush(new User(user1Username, passwordEncoder.encode(user1Password)));

        authService.signIn(new AuthRequest(user1Username, user1Password), request);

        Authentication initialAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(initialAuth, "User1 should be authenticated initially");
        assertEquals(user1Username, ((UserDetailsImpl) initialAuth.getPrincipal()).getUsername());

        String user2Username = "user2";
        String user2Password = "pass2";
        userRepository.saveAndFlush(new User(user2Username, passwordEncoder.encode(user2Password)));

        AuthRequest signInRequestForUser2 = new AuthRequest(user2Username, user2Password);

        CloudStorageException exception = assertThrows(CloudStorageException.class,
                () -> authService.signIn(signInRequestForUser2, request));

        assertEquals("User is already authenticated. Cannot authenticate again!", exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());

        Authentication finalAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(finalAuth);
        assertEquals(user1Username, ((UserDetailsImpl) finalAuth.getPrincipal()).getUsername(),
                "Authentication should still be for user1 after failed signIn attempt");
    }

}