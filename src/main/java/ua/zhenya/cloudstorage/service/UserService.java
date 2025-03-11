package ua.zhenya.cloudstorage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.zhenya.cloudstorage.dto.UserCreateRequest;
import ua.zhenya.cloudstorage.dto.UserResponse;

public interface UserService {
    UserResponse createUser(UserCreateRequest userCreateRequest);
    UserResponse getUser(Integer id);
}
