package ua.zhenya.cloudstorage.service;

import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.dto.ResourceResponse;

import java.io.InputStream;
import java.util.List;

public interface ResourceService {
    List<ResourceResponse> uploadResources(Integer userId, String path, MultipartFile[] files);

    ResourceResponse getResourceInfo(Integer userId, String path);

    ResourceResponse createDirectory(Integer userId, String path);

    List<ResourceResponse> getDirectoryContent(Integer userId, String path);

    void deleteResource(Integer userId, String path);

    InputStream downloadResource(Integer userId, String path);

    ResourceResponse moveResource(Integer userId, String from, String to);

    List<ResourceResponse> searchResources(Integer userId, String query);
}
