package ua.zhenya.cloudstorage.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ua.zhenya.cloudstorage.dto.UserCreateRequest;
import ua.zhenya.cloudstorage.dto.UserResponse;
import ua.zhenya.cloudstorage.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toEntity(UserCreateRequest userCreateRequest);
    UserResponse toResponse(User user);
}
