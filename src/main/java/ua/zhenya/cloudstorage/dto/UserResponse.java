package ua.zhenya.cloudstorage.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private String username;
}
