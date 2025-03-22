package ua.zhenya.cloudstorage.service.impl;

import io.minio.Result;
import io.minio.StatObjectResponse;
import io.minio.errors.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.dto.ResourceDownloadResponse;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.dto.ResourceType;
import ua.zhenya.cloudstorage.exception.CloudStorageException;
import ua.zhenya.cloudstorage.service.MinioService;
import ua.zhenya.cloudstorage.service.ResourceService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ua.zhenya.cloudstorage.utils.Constants.*;
import static ua.zhenya.cloudstorage.utils.PathUtils.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceServiceImpl implements ResourceService {
    private final MinioService minioService;

    @Override
    @Transactional
    public void createDirectoryForUser(Integer id) {
        String userDirectoryPath = USER_DIRECTORY_PATH.formatted(id);
        try {
            minioService.createDirectory(userDirectoryPath);
        } catch (Exception e) {
            throw new CloudStorageException("Failed to create user directory!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public List<ResourceResponse> uploadResources(Integer userId, String path, MultipartFile[] files) {
        if (!isDirectory(path))
            throw new CloudStorageException("Invalid path: must be a directory!", HttpStatus.NOT_FOUND);

        String fullRelativePath = buildPath(userId, path);
        if (!minioService.objectExists(fullRelativePath))
            throw new CloudStorageException("Target directory not found!", HttpStatus.NOT_FOUND);

        List<ResourceResponse> uploadedResources = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                String fileAbsolutePath = fullRelativePath + originalFilename;
                if (minioService.objectExists(fileAbsolutePath)) {
                    throw new CloudStorageException("File already exists!", HttpStatus.CONFLICT);
                }
                minioService.uploadObject(fileAbsolutePath, file.getInputStream(), file.getSize(), file.getContentType());
                uploadedResources.add(new ResourceResponse(
                        getCorrectResponsePath(fileAbsolutePath),
                        getFileOrFolderName(fileAbsolutePath),
                        file.getSize(),
                        ResourceType.FILE
                ));
            }
        } catch (Exception e) {
            throw new CloudStorageException("Error uploading file(s)!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return uploadedResources;
    }

    @Override
    public ResourceResponse getResourceInfo(Integer userId, String path) {
        String absolutePath = buildPath(userId, path);
        if (!minioService.objectExists(absolutePath))
            throw new CloudStorageException("Resource not found!", HttpStatus.NOT_FOUND);

        ResourceType resourceType = extractResourceType(absolutePath);
        ResourceResponse resourceResponse;
        try {
            StatObjectResponse objectInfo = minioService.getObjectInfo(absolutePath);
            resourceResponse = new ResourceResponse(
                    getCorrectResponsePath(absolutePath),
                    getFileOrFolderName(absolutePath),
                    resourceType == ResourceType.FILE ? objectInfo.size() : null,
                    resourceType);
        } catch (Exception e) {
            throw new CloudStorageException("Error on getting resource info!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return resourceResponse;
    }

    @Override
    @Transactional
    public ResourceResponse createDirectory(Integer userId, String path) {
        if (!isDirectory(path))
            throw new CloudStorageException("Invalid path: must be a directory!", HttpStatus.BAD_REQUEST);

        String absolutePath = buildPath(userId, path);
        if (minioService.objectExists(absolutePath))
            throw new CloudStorageException("Resource already exists!", HttpStatus.CONFLICT);

        String relativePath = getRelativePath(absolutePath);
        if (!minioService.objectExists(relativePath))
            throw new CloudStorageException("Parent directory not found!", HttpStatus.NOT_FOUND);

        ResourceResponse resourceResponse;
        try {
            minioService.createDirectory(absolutePath);
            resourceResponse = new ResourceResponse(
                    getCorrectResponsePath(absolutePath),
                    getFileOrFolderName(absolutePath),
                    null,
                    ResourceType.DIRECTORY
            );
        } catch (Exception e) {
            throw new CloudStorageException("Something went wrong!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return resourceResponse;
    }

    @Override
    public List<ResourceResponse> getDirectoryContent(Integer userId, String path) {
        if (!isDirectory(path)) {
            throw new CloudStorageException("Invalid path: must be a directory!", HttpStatus.BAD_REQUEST);
        }

        String absolutePath = buildPath(userId, path);
        if (!minioService.objectExists(absolutePath))
            throw new CloudStorageException("Directory not found!", HttpStatus.NOT_FOUND);

        List<ResourceResponse> resourceResponses = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioService.listObjects(absolutePath, false);
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                if (objectName.equals(absolutePath)) {
                    continue;
                }

                ResourceType resourceType = extractResourceType(objectName);
                ResourceResponse resourceResponse = new ResourceResponse(
                        getCorrectResponsePath(objectName),
                        getFileOrFolderName(objectName),
                        resourceType == ResourceType.FILE ? item.size() : null,
                        resourceType
                );
                resourceResponses.add(resourceResponse);
            }
        } catch (Exception e) {
            throw new CloudStorageException("Something went wrong!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return resourceResponses;
    }

    @Override
    public ResourceDownloadResponse downloadResource(Integer userId, String path) {
        String absolutePath = buildPath(userId, path);
        if (!minioService.objectExists(absolutePath))
            throw new CloudStorageException("Resource not found!", HttpStatus.NOT_FOUND);

        try {
            String filename;
            Resource content;
            if (isDirectory(absolutePath)) {
                content = createZipArchive(absolutePath);
                filename = getFileOrFolderName(absolutePath) + ".zip";
            } else {
                InputStream fileInputStream = minioService.getObject(absolutePath);
                content = new InputStreamResource(fileInputStream);
                filename = getFileOrFolderName(absolutePath);
            }
            return new ResourceDownloadResponse(filename, content);
        } catch (Exception e) {
            throw new CloudStorageException("Error while downloading resource!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public ResourceResponse moveResource(Integer userId, String from, String to) {
        String absoluteFromPath = buildPath(userId, from);
        if (!minioService.objectExists(absoluteFromPath))
            throw new CloudStorageException("Resource not found!", HttpStatus.NOT_FOUND);

        String absoluteToPath = buildPath(userId, to);
        if (minioService.objectExists(absoluteToPath))
            throw new CloudStorageException("Target path already exists!", HttpStatus.CONFLICT);

        if (isDirectory(absoluteFromPath) && !isDirectory(absoluteToPath))
            throw new CloudStorageException("Invalid target path!", HttpStatus.BAD_REQUEST);

        try {
            if (isDirectory(to))
                createEmptyObjectIfNotExist(absoluteToPath);

            ResourceType actualResourceType = extractResourceType(absoluteFromPath);
            moveDirectoryRecursively(absoluteFromPath, absoluteToPath);
            StatObjectResponse objectInfo = minioService.getObjectInfo(absoluteToPath);

            return new ResourceResponse(
                    getCorrectResponsePath(absoluteToPath),
                    getFileOrFolderName(absoluteToPath),
                    actualResourceType == ResourceType.FILE ? objectInfo.size() : null,
                    actualResourceType
            );
        } catch (Exception e) {
            throw new CloudStorageException("Something went wrong!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<ResourceResponse> searchResources(Integer userId, String query) {
        String userDirectoryPath = USER_DIRECTORY_PATH.formatted(userId);
        List<ResourceResponse> resourceResponses = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioService.listObjects(userDirectoryPath, true);
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                String resourceName = getFileOrFolderName(objectName);

                if (resourceName.toLowerCase().contains(query.toLowerCase())) {
                    ResourceType resourceType = extractResourceType(objectName);
                    resourceResponses.add(new ResourceResponse(
                            getCorrectResponsePath(objectName),
                            resourceName,
                            resourceType == ResourceType.FILE ? item.size() : null,
                            resourceType
                    ));
                }
            }
        } catch (Exception e) {
            throw new CloudStorageException("Something went wrong!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return resourceResponses;
    }

    @Override
    @Transactional
    public void deleteResource(Integer userId, String path) {
        String absolutePath = buildPath(userId, path);
        if (!minioService.objectExists(absolutePath))
            throw new CloudStorageException("Resource not found!", HttpStatus.NOT_FOUND);

        try {
            deleteDirectoryRecursively(absolutePath);
        } catch (Exception e) {
            throw new CloudStorageException("Something went wrong!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Resource createZipArchive(String directoryPath) throws IOException, ServerException, InsufficientDataException,
            ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidResponseException, XmlParserException, InternalException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        Iterable<Result<Item>> objects = minioService.listObjects(directoryPath, true);

        for (Result<Item> result : objects) {
            Item item = result.get();
            String objectName = item.objectName();

            if (objectName.equals(directoryPath) || isDirectory(objectName))
                continue;

            try (InputStream inputStream = minioService.getObject(objectName)) {
                String relativePath = objectName.substring(directoryPath.length());
                ZipEntry zipEntry = new ZipEntry(relativePath);
                zos.putNextEntry(zipEntry);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
            }
        }
        zos.finish();
        return new ByteArrayResource(baos.toByteArray());
    }

    private void deleteDirectoryRecursively(String absolutePath) throws
            ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        Iterable<Result<Item>> objects = minioService.listObjects(absolutePath, true);

        for (Result<Item> result : objects) {
            minioService.deleteObject(result.get().objectName());
        }
    }

    private void moveDirectoryRecursively(String from, String to) throws Exception {
        Iterable<Result<Item>> objects = minioService.listObjects(from, true);

        for (Result<Item> result : objects) {
            String oldPath = result.get().objectName();
            String newPath = oldPath.replace(from, to);
            createIntermediateDirectoriesIfNeeded(newPath);

            minioService.moveObject(oldPath, newPath);
        }
    }

    private void createEmptyObjectIfNotExist(String path) throws Exception {
        if (!minioService.objectExists(path)) {
            minioService.createDirectory(path);
        }
    }

    private void createIntermediateDirectoriesIfNeeded(String path) throws Exception {
        String[] pathParts = path.split("/");

        StringBuilder currentDirPath = new StringBuilder();
        for (int i = 0; i < pathParts.length - 1; i++) {
            currentDirPath.append(pathParts[i]).append("/");
            String directoryPath = currentDirPath.toString();
            createEmptyObjectIfNotExist(directoryPath);
        }
    }
}
