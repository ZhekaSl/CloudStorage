package ua.zhenya.cloudstorage.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import ua.zhenya.cloudstorage.model.User;

@Getter
public class UserRegisteredEvent extends ApplicationEvent {
    private final User user;

    public UserRegisteredEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
