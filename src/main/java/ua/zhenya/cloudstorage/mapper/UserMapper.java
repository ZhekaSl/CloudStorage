package ua.zhenya.cloudstorage.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ua.zhenya.cloudstorage.dto.AuthRequest;
import ua.zhenya.cloudstorage.dto.AuthResponse;
import ua.zhenya.cloudstorage.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    User toEntity(AuthRequest authRequest);
    AuthResponse toResponse(User user);
}
