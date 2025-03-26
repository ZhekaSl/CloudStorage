package ua.zhenya.cloudstorage.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ua.zhenya.cloudstorage.dto.AuthRequest;
import ua.zhenya.cloudstorage.dto.AuthResponse;

public interface AuthService {
    AuthResponse signUp(AuthRequest authRequest, HttpServletRequest request);

    AuthResponse signIn(AuthRequest request, HttpServletRequest httpServletRequest);

    void signOut(HttpServletRequest request, HttpServletResponse response);

    AuthResponse getCurrentUser();

}
