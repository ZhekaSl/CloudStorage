package ua.zhenya.cloudstorage.event.listeners;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.zhenya.cloudstorage.event.UserRegisteredEvent;
import ua.zhenya.cloudstorage.model.User;
import ua.zhenya.cloudstorage.service.MinioService;

@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {
    private final MinioService minioService;

    @EventListener
    @Transactional
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        User user = event.getUser();
        minioService.createUserDirectory(user.getId());
    }
}
