package ua.zhenya.cloudstorage.service.impl;

import io.minio.Result;
import io.minio.StatObjectResponse;
import io.minio.errors.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.dto.ResourceType;
import ua.zhenya.cloudstorage.service.MinioService;
import ua.zhenya.cloudstorage.service.ResourceService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static ua.zhenya.cloudstorage.utils.Constants.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceServiceImpl implements ResourceService {
    private final MinioService minioService;

    public void createDirectoryForUser(Integer id) {
        String userDirectoryPath = "user-" + id + "-files/";
        try {
            minioService.createDirectory(userDirectoryPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SneakyThrows
    @Transactional
    public List<ResourceResponse> uploadResources(Integer userId, String path, MultipartFile[] files) {
        if (path == null || !path.endsWith("/"))
            throw new IllegalArgumentException("Invalid path");

        String fullRelativePath = buildPath(userId, path);
        if (!minioService.objectExists(fullRelativePath))
            throw new IllegalArgumentException("Directory to upload does not exist");

        List<ResourceResponse> uploadedResources = new ArrayList<>();
        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            String fileAbsolutePath = fullRelativePath + originalFilename;
            if (minioService.objectExists(fileAbsolutePath)) {
                throw new RuntimeException("File already exists: " + fileAbsolutePath);
            }
            minioService.uploadObject(fileAbsolutePath, file.getInputStream(), file.getSize(), file.getContentType());
            uploadedResources.add(createResourceResponse(
                    getCorrectResponsePath(fileAbsolutePath),
                    getFileOrFolderName(fileAbsolutePath),
                    file.getSize(),
                    ResourceType.FILE
            ));
        }
        return uploadedResources;
    }

    @Override
    public ResourceResponse getResourceInfo(Integer userId, String path) {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("Invalid path");

        String absolutePath = buildPath(userId, path);
        if (!minioService.objectExists(absolutePath))
            throw new IllegalArgumentException("Resource does not exist: " + path);

        ResourceType resourceType = extractResourceType(absolutePath);
        ResourceResponse resourceResponse;
        try {
            StatObjectResponse objectInfo = minioService.getObjectInfo(absolutePath);
            resourceResponse = createResourceResponse(
                    getCorrectResponsePath(absolutePath),
                    getFileOrFolderName(absolutePath),
                    resourceType == ResourceType.FILE ? objectInfo.size() : null,
                    resourceType);
        } catch (Exception e) {
            throw new RuntimeException("Error on getting resource info: ", e);
        }
        return resourceResponse;
    }

    @Override
    @Transactional
    public ResourceResponse createDirectory(Integer userId, String path) {
        if (path == null || !path.endsWith("/"))
            throw new IllegalArgumentException("Invalid path");

        String absolutePath = buildPath(userId, path);
        if (minioService.objectExists(absolutePath))
            throw new RuntimeException("Resource already exists: " + absolutePath);

        String relativePath = getRelativePath(absolutePath) + "/";
        if (!minioService.objectExists(relativePath))
            throw new RuntimeException("Parent directory not exists: " + relativePath);

        ResourceResponse resourceResponse;
        try {
            minioService.createDirectory(absolutePath);
            resourceResponse = createResourceResponse(
                    getCorrectResponsePath(absolutePath),
                    getFileOrFolderName(absolutePath),
                    null,
                    ResourceType.DIRECTORY
            );
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong", e.getCause());
        }
        return resourceResponse;
    }

    @Override
    public List<ResourceResponse> getDirectoryContent(Integer userId, String path) {
        return List.of();
    }

    @Override
    public InputStream downloadResource(Integer userId, String path) {
        return null;
    }

    @Override
    @Transactional
    public ResourceResponse moveResource(Integer userId, String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("Invalid path: 'from' and 'to' must not be empty");
        }

        String absoluteFromPath = buildPath(userId, from);
        String absoluteToPath = buildPath(userId, to);

        if (!minioService.objectExists(absoluteFromPath)) {
            throw new RuntimeException("Resource not found: " + absoluteFromPath);
        }

        if (minioService.objectExists(absoluteToPath)) {
            throw new RuntimeException("Target path already exists: " + absoluteToPath);
        }

        ResourceType actualResourceType = extractResourceType(absoluteFromPath);
        try {

            moveDirectoryRecursively(absoluteFromPath, absoluteToPath);

            StatObjectResponse objectInfo = minioService.getObjectInfo(absoluteToPath);
            return new ResourceResponse(
                    getCorrectResponsePath(absoluteToPath),
                    getFileOrFolderName(absoluteToPath),
                    actualResourceType == ResourceType.FILE ? objectInfo.size() : null,
                    actualResourceType
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ResourceResponse> searchResources(Integer userId, String query) {
        return List.of();
    }

    @Override
    @Transactional
    public void deleteResource(Integer userId, String path) {
        if (path == null || path.isBlank())
            throw new IllegalArgumentException("Invalid path");

        String absolutePath = buildPath(userId, path);
        if (!minioService.objectExists(absolutePath))
            throw new RuntimeException("Resource does not exist: " + absolutePath);

        try {
            Iterable<Result<Item>> objects = minioService.listObjects(absolutePath);
            boolean isDirectory = objects.iterator().hasNext();

            if (isDirectory) {
                deleteDirectoryRecursively(objects);
            } else {
                minioService.deleteObject(absolutePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong");
        }
    }

    private void deleteDirectoryRecursively(Iterable<Result<Item>> objects) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        for (Result<Item> result : objects) {
            minioService.deleteObject(result.get().objectName());
        }
    }

    private void moveDirectoryRecursively(String from, String to) throws Exception {
        Iterable<Result<Item>> objects = minioService.listObjects(from);

        for (Result<Item> object : objects) {
            String oldPath = object.get().objectName();
            String newPath = oldPath.replace(from, to);
            minioService.moveObject(oldPath, newPath);
        }
    }

    private ResourceResponse createResourceResponse(String path, String name, Long size, ResourceType type) {
        return ResourceResponse.builder()
                .path(path)
                .name(name)
                .type(type)
                .size(size)
                .build();
    }

    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private String getFileOrFolderName(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return Paths.get(path).getFileName().toString();
    }

    private String getRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "/";
        }

        if (absolutePath.endsWith("/")) {
            absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
        }

        int lastSlashIndex = absolutePath.lastIndexOf("/");

        if (lastSlashIndex == -1) {
            return absolutePath;
        }

        return ensureTrailingSlash(absolutePath.substring(0, lastSlashIndex));
    }

    private String buildPath(Integer userId, String path) {
        String userDirectory = USER_DIRECTORY_PATH.formatted(userId);

        if (path == null || path.isBlank() || path.equals("/")) {
            return userDirectory;
        }

        return userDirectory + path;
    }

    private String getCorrectResponsePath(String absolutePath) {
        String relativePath = getRelativePath(absolutePath);

        int firstSlashIndex = relativePath.indexOf("/");
        if (firstSlashIndex == -1) {
            return "/";
        }

        String trimmedPath = relativePath.substring(firstSlashIndex + 1);
        return trimmedPath.isBlank() ? "/" : trimmedPath;
    }

    private ResourceType extractResourceType(String absolutePath) {
        return absolutePath.endsWith("/") ? ResourceType.DIRECTORY : ResourceType.FILE;
    }
}
