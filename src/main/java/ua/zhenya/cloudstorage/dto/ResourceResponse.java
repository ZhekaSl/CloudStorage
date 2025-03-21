package ua.zhenya.cloudstorage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ResourceResponse {
    private String path;
    private String name;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long size;
    private ResourceType type;
}
