package ua.zhenya.cloudstorage.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.BaseIntegrationTest;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.dto.ResourceType;
import ua.zhenya.cloudstorage.service.MinioService;
import ua.zhenya.cloudstorage.service.ResourceService;
import ua.zhenya.cloudstorage.testdata.TestConstants;
import ua.zhenya.cloudstorage.utils.Constants;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceServiceImplTest extends BaseIntegrationTest {
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private MinioService minioService;

    @Test
    void uploadResources_uploadsOneResource() throws IOException {
        int userId = 1;
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(userId);
        ClassPathResource jpeg = new ClassPathResource(TestConstants.NATURE_JPG_PATH);


        MockMultipartFile multipartFile = new MockMultipartFile("file", jpeg.getFilename(), "image/jpeg", jpeg.getInputStream());

        List<ResourceResponse> resourceResponses = resourceService.uploadResources(userId, userDirectoryPath, new MultipartFile[]{multipartFile});

        assertEquals(1, resourceResponses.size());
        assertEquals(userDirectoryPath, resourceResponses.getFirst().getPath());
        assertEquals(jpeg.getFilename(), resourceResponses.getFirst().getName());
        assertEquals(multipartFile.getSize(), resourceResponses.getFirst().getSize());
        assertEquals(ResourceType.FILE, resourceResponses.getFirst().getType());

        String fullPath = userDirectoryPath + jpeg.getFilename();
        assertTrue(minioService.objectExists(fullPath));
    }

    @Test
    void uploadResources_uploadsMultipleResources() throws IOException {
        int userId = 1;
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(userId);
        ClassPathResource jpeg = new ClassPathResource(TestConstants.NATURE_JPG_PATH);
        ClassPathResource txt = new ClassPathResource(TestConstants.RANDOM_TXT_PATH);
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", jpeg.getFilename(), "image/jpeg", jpeg.getInputStream()),
                new MockMultipartFile("file", txt.getFilename(), "text/plain", txt.getInputStream()),
        };

        List<ResourceResponse> resourceResponses = resourceService.uploadResources(userId, userDirectoryPath, resources);

        assertEquals(2, resourceResponses.size());

        assertElementsInOrder(resourceResponses, ResourceResponse::getName, List.of(jpeg.getFilename(), txt.getFilename()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getPath, List.of(userDirectoryPath, userDirectoryPath));
        assertElementsInOrder(resourceResponses, ResourceResponse::getSize, List.of(resources[0].getSize(), resources[1].getSize()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getType, List.of(ResourceType.FILE, ResourceType.FILE));

        assertTrue(minioService.objectExists(userDirectoryPath + jpeg.getFilename()));
    }

    @Test
    void uploadResources_uploadsResourceWithSubdirectory() throws IOException {
        int userId = 1;
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(userId);
        ClassPathResource jpeg = new ClassPathResource(TestConstants.NATURE_JPG_PATH);
        ClassPathResource txt = new ClassPathResource(TestConstants.RANDOM_TXT_PATH);
        String innerPath = "somePath/innerPath/";


        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", innerPath + jpeg.getFilename(), "image/jpeg", jpeg.getInputStream()),
                new MockMultipartFile("file", innerPath + txt.getFilename(), "text/plain", txt.getInputStream()),
        };

        List<ResourceResponse> resourceResponses = resourceService.uploadResources(userId, userDirectoryPath, resources);

        assertElementsInOrder(resourceResponses, ResourceResponse::getName, List.of(jpeg.getFilename(), txt.getFilename()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getPath, List.of(userDirectoryPath + innerPath, userDirectoryPath + innerPath));
        assertElementsInOrder(resourceResponses, ResourceResponse::getSize, List.of(resources[0].getSize(), resources[1].getSize()));


    }

    @Test
    void uploadResources_throwsConflict_fileAlreadyExists() throws IOException {
        int userId = 1;
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(userId);
        ClassPathResource jpeg = new ClassPathResource(TestConstants.NATURE_JPG_PATH);

        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", jpeg.getFilename(), "image/jpeg", jpeg.getInputStream()),
        };

        resourceService.uploadResources(userId, userDirectoryPath, resources);

        assertThrows(RuntimeException.class, () -> resourceService.uploadResources(userId, userDirectoryPath, resources));
    }
}