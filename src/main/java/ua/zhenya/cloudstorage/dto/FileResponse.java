package ua.zhenya.cloudstorage.dto;

import lombok.Builder;
import lombok.Data;
import ua.zhenya.cloudstorage.model.ResourceType;

@Data
@Builder
public class FileResponse {
    private String path;
    private String name;
    private Long size;
    private ResourceType type;
}
