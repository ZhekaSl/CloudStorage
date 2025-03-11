package ua.zhenya.cloudstorage.dto;

import lombok.Builder;
import lombok.Data;
import ua.zhenya.cloudstorage.model.ResourceType;

@Data
@Builder
public class DirectoryResponse {
    private String path;
    private String name;
    private ResourceType type;
}
