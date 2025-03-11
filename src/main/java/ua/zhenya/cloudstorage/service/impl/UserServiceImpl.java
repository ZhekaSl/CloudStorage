package ua.zhenya.cloudstorage.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.zhenya.cloudstorage.dto.UserCreateRequest;
import ua.zhenya.cloudstorage.dto.UserResponse;
import ua.zhenya.cloudstorage.event.UserRegisteredEvent;
import ua.zhenya.cloudstorage.mapper.UserMapper;
import ua.zhenya.cloudstorage.model.User;
import ua.zhenya.cloudstorage.repository.UserRepository;
import ua.zhenya.cloudstorage.service.UserService;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest userCreateRequest) {
        if (userRepository.findByUsername(userCreateRequest.getUsername()) != null) {
            throw new RuntimeException("User already exists");
        }
        User savedUser = userRepository.save(userMapper.toEntity(userCreateRequest));
        eventPublisher.publishEvent(new UserRegisteredEvent(this, savedUser));

        return userMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse getUser(Integer id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow();
    }
}
