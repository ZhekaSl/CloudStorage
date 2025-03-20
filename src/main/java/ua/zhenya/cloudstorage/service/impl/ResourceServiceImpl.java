package ua.zhenya.cloudstorage.service.impl;

import io.minio.Result;
import io.minio.StatObjectResponse;
import io.minio.errors.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
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
    public List<ResourceResponse> uploadResources(Integer userId, String path, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No files provided");
        }
        List<ResourceResponse> uploadedResources = new ArrayList<>();
        String fullRelativePath = buildPath(userId, path);

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new IllegalArgumentException("Invalid file name");
            }
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
        String absolutePath = buildPath(userId, path);
        ResourceType resourceType = extractResourceType(absolutePath);
        ResourceResponse resourceResponse;
        try {
            StatObjectResponse objectInfo = minioService.getObjectInfo(absolutePath);
            resourceResponse = createResourceResponse(
                    getCorrectResponsePath(absolutePath),
                    getFileOrFolderName(absolutePath),
                    resourceType == ResourceType.FILE ? objectInfo.size() : null,
                    resourceType);
        } catch (IOException | ServerException | InsufficientDataException | ErrorResponseException |
                 NoSuchAlgorithmException | InvalidKeyException | InvalidResponseException | XmlParserException |
                 InternalException e) {
            throw new RuntimeException("Error uploading file: ", e);
        }
        return resourceResponse;
    }

    @Override
    public ResourceResponse createDirectory(Integer userId, String path) {
        String fullPath = String.format("user-%d-files/%s/", userId, path);
        String folderName = path.substring(path.lastIndexOf('/') + 1);
        try {
            minioService.createDirectory(fullPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ResourceResponse.builder()
                .path(path)
                .name(folderName)
                .build();
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
    public ResourceResponse moveResource(Integer userId, String from, String to) {
        return null;
    }

    @Override
    public List<ResourceResponse> searchResources(Integer userId, String query) {
        return List.of();
    }

    @Override
    public void deleteResource(Integer userId, String path) {
        if (path == null || path.isBlank())
            throw new IllegalArgumentException("Invalid path");

        String absolutePath = buildPath(userId, path);
        try {
            Iterable<Result<Item>> objects = minioService.listObjects(absolutePath);

            if (!minioService.objectExists(absolutePath))
                throw new RuntimeException("File does not exist: " + absolutePath);

            if (objects.iterator().hasNext()) {
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
        // Проверяем на пустоту и null
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "/";
        }

        // Если путь заканчивается на слеш, убираем его
        if (absolutePath.endsWith("/")) {
            absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
        }

        int lastSlashIndex = absolutePath.lastIndexOf("/");

        if (lastSlashIndex == -1) {
            return absolutePath;
        }

        return absolutePath.substring(0, lastSlashIndex);
    }

    private String buildPath(Integer userId, String path) {
        String userDirectory = USER_DIRECTORY_PATH.formatted(userId);

        if (path == null || path.isBlank() || path.equals("/")) {
            return userDirectory;
        }

        return userDirectory + path; // Не добавляем слеш к файлу
    }

    private String getCorrectResponsePath(String absolutePath) {
        String relativePath = getRelativePath(absolutePath) + "/";

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
