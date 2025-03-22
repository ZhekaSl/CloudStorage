package ua.zhenya.cloudstorage.event.listeners;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ua.zhenya.cloudstorage.event.UserRegisteredEvent;
import ua.zhenya.cloudstorage.model.User;
import ua.zhenya.cloudstorage.service.ResourceService;

@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {
    private final ResourceService resourceService;

    @TransactionalEventListener
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        User user = event.getUser();
        resourceService.createDirectoryForUser(user.getId());
    }
}
