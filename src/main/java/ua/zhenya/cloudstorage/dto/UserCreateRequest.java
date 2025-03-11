package ua.zhenya.cloudstorage.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCreateRequest {
    private String username;
    private String password;
}
