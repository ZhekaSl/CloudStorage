package ua.zhenya.cloudstorage.service.impl;

import io.minio.errors.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.BaseIntegrationTest;
import ua.zhenya.cloudstorage.dto.ResourceDownloadResponse;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.dto.ResourceType;
import ua.zhenya.cloudstorage.exception.CloudStorageException;
import ua.zhenya.cloudstorage.service.MinioService;
import ua.zhenya.cloudstorage.service.ResourceService;
import ua.zhenya.cloudstorage.testdata.TestData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static ua.zhenya.cloudstorage.testdata.TestConstants.*;
import static ua.zhenya.cloudstorage.utils.PathUtils.*;

class ResourceServiceImplTest extends BaseIntegrationTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private MinioService minioService;

    @ParameterizedTest
    @EmptySource
    @CsvSource({
            "myfolder/",
            "myfolder/inner/"
    })
    void uploadResource_shouldUploadResource(String path) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        MultipartFile multipartFile = TestData.getRandomMultipartFile();
        String relativePath = buildPath(path);
        minioService.createDirectory(relativePath);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, path, List.of(multipartFile));

        assertEquals(1, resourceResponses.size());
        assertEquals(path, resourceResponses.getFirst().getPath());
        assertEquals(multipartFile.getOriginalFilename(), resourceResponses.getFirst().getName());
        assertEquals(multipartFile.getSize(), resourceResponses.getFirst().getSize());
        assertEquals(ResourceType.FILE, resourceResponses.getFirst().getType());

        String fullPath = relativePath + multipartFile.getOriginalFilename();
        assertTrue(minioService.objectExists(fullPath));
    }

    @Test
    void uploadResources_uploadsMultipleResources() {
        List<MultipartFile> resources = List.of(
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile()
        );

        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, "", resources);

        assertEquals(2, resourceResponses.size());
        assertElementsInOrder(resourceResponses,
                ResourceResponse::getName, resources,
                file -> getResourceName(file.getOriginalFilename()), Objects::equals);
        assertElementsInOrder(resourceResponses,
                ResourceResponse::getPath, resources,
                file -> getResponsePath(file.getOriginalFilename()), Objects::equals);
        assertElementsInOrder(resourceResponses,
                ResourceResponse::getSize, resources,
                MultipartFile::getSize, Objects::equals);

        assertTrue(minioService.objectExists(buildPath(resources.get(0).getOriginalFilename())));
        assertTrue(minioService.objectExists(buildPath(resources.get(1).getOriginalFilename())));
    }


    @Test
    void uploadResources_uploadsResourceWithSubdirectoryInFilename() throws Exception {
        String pathInFilename1 = "somePath/jpeg/";
        String pathInFilename2 = "somePath/innerFolder/txt/";
        List<MultipartFile> resources = List.of(
                TestData.getRandomMultipartFile(pathInFilename1),
                TestData.getRandomMultipartFile(pathInFilename2)
        );

        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, "", resources);

        assertElementsInOrder(resourceResponses,
                ResourceResponse::getName, resources,
                file -> getResourceName(file.getOriginalFilename()), Objects::equals);
        assertElementsInOrder(resourceResponses,
                ResourceResponse::getPath, resources,
                file -> getRelativePath(file.getOriginalFilename()), Objects::equals);
        assertElementsInOrder(resourceResponses,
                ResourceResponse::getSize, resources,
                MultipartFile::getSize, Objects::equals);

        assertTrue(minioService.objectExists(buildPath(resources.get(0).getOriginalFilename())));
        assertTrue(minioService.objectExists(buildPath(resources.get(1).getOriginalFilename())));

        checkIntermediateFoldersExist(pathInFilename1);
        checkIntermediateFoldersExist(pathInFilename2);
    }

    @Test
    void uploadResources_throws_whenFileAlreadyExists() {
        List<MultipartFile> resources = List.of(
                TestData.getRandomMultipartFile()
        );

        resourceService.uploadResources(USER_1_ID, "", resources);

        assertThrows(CloudStorageException.class, () -> resourceService.uploadResources(USER_1_ID, "", resources));
    }

    @Test
    void uploadResources_throws_whenTargetDirectoryNotExists() {
        List<MultipartFile> resources = List.of(
                TestData.getRandomMultipartFile()
        );
        assertThrows(CloudStorageException.class, () -> resourceService.uploadResources(USER_1_ID, "unknownFolder/", resources));
    }

    @Test
    void getResourceInfo_shouldReturnFileInfo_whenResourceExists() throws Exception {
        String path = "myfolder/images/";
        MultipartFile multipartFile = TestData.getRandomMultipartFile();
        String relativePath = path + multipartFile.getOriginalFilename();

        Long size = createTestResource(buildPath(relativePath), multipartFile);

        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, relativePath);

        assertEquals(multipartFile.getOriginalFilename(), resourceInfo.getName());
        assertEquals(path, resourceInfo.getPath());
        assertEquals(size, resourceInfo.getSize());
        assertEquals(ResourceType.FILE, resourceInfo.getType());
    }

    @Test
    void getResourceInfo_throwsException_whenFileNotExists() {
        String absolutePath = buildPath("unknown/directory/text.txt");

        assertThrows(CloudStorageException.class, () -> resourceService.getResourceInfo(USER_1_ID, absolutePath));
    }

    @Test
    void getResourceInfo_shouldReturnDirectoryInfo_whenDirectoryExists() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String path = "mydirectory/images/";
        String absolutePath = buildPath(path);

        minioService.createDirectory(absolutePath);
        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, path);

        assertEquals(getResourceName(path), resourceInfo.getName());
        assertEquals(getRelativePath(path), resourceInfo.getPath());
        assertNull(resourceInfo.getSize());
        assertEquals(ResourceType.DIRECTORY, resourceInfo.getType());
    }

    @Test
    void getResourceInfo_throwsException_whenDirectoryNotExists() {
        String absolutePath = buildPath("unknown/directory/");

        assertThrows(CloudStorageException.class, () -> resourceService.getResourceInfo(USER_1_ID, absolutePath));
    }

    @ParameterizedTest
    @CsvSource({
            "myfolder/images/nature.jpg, nature.jpg, myfolder/images/, FILE",
            "myfolder/images/, images, myfolder/, DIRECTORY",
            "rootfolder/, rootfolder,'', DIRECTORY",
            "/nature.jpg, nature.jpg,'', FILE",
    })
    void getResourceInfo_shouldReturnResourceInfo_whenResourceExists(String path, String expectedName, String expectedPath, ResourceType expectedType) throws Exception {
        MultipartFile multipartFileJpeg = TestData.getRandomMultipartFile();
        Long expectedSize = createTestResource(buildPath(path), multipartFileJpeg);

        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, path);
        assertNotNull(resourceInfo);
        assertEquals(expectedName, resourceInfo.getName());
        assertEquals(expectedPath, resourceInfo.getPath());
        assertEquals(expectedType, resourceInfo.getType());
        assertEquals(expectedSize, resourceInfo.getSize());
    }

    @Test
    void getResourceInfo_throwsException_whenResourceNotExists() {
        String path = "resources/images/";

        assertThrows(CloudStorageException.class, () -> resourceService.getResourceInfo(USER_1_ID, path));
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    @Disabled
    void getResourceInfo_throwsException_whenPathIsEmptyOrNull(String path) {
        assertThrows(CloudStorageException.class, () -> resourceService.getResourceInfo(USER_1_ID, path));
    }

    @Test
    public void deleteResource_shouldDeleteExistingFile() throws Exception {
        String path = "mydirectory/images/random.txt";
        String absolutePath = buildPath(path);
        MultipartFile multipartFile = TestData.getRandomMultipartFile();

        createTestResource(absolutePath, multipartFile);
        assertTrue(minioService.objectExists(absolutePath));

        resourceService.deleteResource(USER_1_ID, path);
        assertFalse(minioService.objectExists(absolutePath));
    }

    @Test
    public void deleteResource_shouldDeleteDirectoryRecursively() throws Exception {
        String path = "mydirectory/images/";
        String absolutePath = buildPath(path);
        MultipartFile[] multipartFiles = new MultipartFile[]{
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile("newFolder/"),
        };

        minioService.createDirectory(absolutePath);
        uploadContentToDirectory(absolutePath, multipartFiles);

        resourceService.deleteResource(USER_1_ID, path);
        assertFalse(minioService.objectExists(absolutePath));
        checkObjectsExistence(false, absolutePath, multipartFiles);
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    @CsvSource({
            "unknown/text.txt",
            "unknown/"
    })
    @Disabled
    public void deleteResource_throws_whenResourceNotExists(String path) {
        assertThrows(CloudStorageException.class, () -> resourceService.deleteResource(USER_1_ID, path));
    }

    @ParameterizedTest
    @CsvSource({
            "mydirectory/images/, mydirectory/, images",
            "rootFolder/,'', rootFolder",
    })
    public void createDirectory_shouldCreateEmptyDirectory(String path, String expectedPath, String expectedFolderName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String absolutePath = buildPath(path);

        minioService.createDirectory(buildPath(expectedPath));
        ResourceResponse response = resourceService.createDirectory(USER_1_ID, path);

        assertNotNull(response);
        assertEquals(expectedPath, response.getPath());
        assertEquals(expectedFolderName, response.getName());
        assertEquals(ResourceType.DIRECTORY, response.getType());
        assertNull(response.getSize());

        assertTrue(minioService.objectExists(absolutePath));
    }

    @Test
    public void createDirectory_throws_whenDirectoryAlreadyExists() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String path = "mydirectory/images/newfolder/";
        String absolutePath = buildPath(path);
        minioService.createDirectory(absolutePath);

        assertThrows(CloudStorageException.class, () -> resourceService.createDirectory(USER_1_ID, path));
    }

    @Test
    public void createDirectory_throws_whenParentDirectoryNotExists() {
        String path = "mydirectory/images/newFolder/";

        assertThrows(CloudStorageException.class, () -> resourceService.createDirectory(USER_1_ID, path));
    }

    @ParameterizedTest
    @CsvSource({
            // Only move
            "folder1/random.txt, folder2/random.txt, folder2/, random.txt, FILE",
            "folder1/dir/, folder2/dir/, folder2/, dir, DIRECTORY",
            // Only rename
            "folder1/oldfile.txt, folder1/newfile.txt, folder1/, newfile.txt, FILE",
            "folder1/olddir/, folder1/newdir/, folder1/, newdir, DIRECTORY",
            // Both move and rename
            "folder1/oldfile.txt, folder2/newfile.txt, folder2/, newfile.txt, FILE",
            "folder1/olddir/, folder2/newdir/, folder2/, newdir, DIRECTORY",
            "folder1/folder2/oldfile.txt, newFolder/newfile.txt, newFolder/, newfile.txt, FILE",
            // Moving from root
            "random.txt, folder1/random.txt, folder1/, random.txt, FILE",
            "dir/, folder1/dir/, folder1/, dir, DIRECTORY",
    })
    public void moveResource_shouldMoveResource(String from, String to, String expectedPath, String expectedFilename, ResourceType expectedType) throws Exception {
        MultipartFile multipartFileJpeg = TestData.getRandomMultipartFile();
        String absoluteFromPath = buildPath(from);
        Long expectedSize = createTestResource(absoluteFromPath, multipartFileJpeg);
        assertTrue(minioService.objectExists(absoluteFromPath));

        ResourceResponse resourceInfo = resourceService.moveResource(USER_1_ID, from, to);

        assertNotNull(resourceInfo);
        assertEquals(expectedFilename, resourceInfo.getName());
        assertEquals(expectedPath, resourceInfo.getPath());
        assertEquals(expectedType, resourceInfo.getType());
        assertEquals(expectedSize, resourceInfo.getSize());

        assertFalse(minioService.objectExists(absoluteFromPath));
        assertTrue(minioService.objectExists(buildPath(to)));
    }

    @ParameterizedTest
    @CsvSource({
            // Simple folder move one level up
            "folder1/dir/, folder2/dir/, folder2/, dir",
            // Move with renaming
            "documents/archive/, backup/new_archive/, backup/, new_archive",
            // Moving a folder with multiple nested levels
            "workspace/main/configs/, workspace/backup/configs/, workspace/backup/, configs",
            // Move a root folder into another directory
            "rootFolder/, archive/rootFolder/, archive/, rootFolder",
            // Move and rename simultaneously
            "data/old_name/, data/new_name/, data/, new_name",
            // Move a deeply nested structure
            "x/y/z/a/b/c/, new_x/new_y/new_z/a/b/c/, new_x/new_y/new_z/a/b/, c"
    })
    public void moveResource_shouldMoveFolderWithContent(String from, String to, String expectedPath, String expectedFolderName) throws Exception {
        MultipartFile[] multipartFiles = new MultipartFile[]{
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile("newFolder/"),
        };
        String absoluteFromPath = buildPath(from);
        minioService.createDirectory(absoluteFromPath);
        uploadContentToDirectory(absoluteFromPath, multipartFiles);
        checkObjectsExistence(true, absoluteFromPath, multipartFiles);

        ResourceResponse response = resourceService.moveResource(USER_1_ID, from, to);

        assertNotNull(response);
        assertEquals(expectedFolderName, response.getName());
        assertEquals(expectedPath, response.getPath());
        assertEquals(ResourceType.DIRECTORY, response.getType());

        checkIntermediateFoldersExist(to);
        checkContentMoved(from, to, multipartFiles);
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    @Disabled
    public void moveResource_throws_whenFromPathIsIncorrect(String from) {
        assertThrows(CloudStorageException.class, () -> resourceService.moveResource(USER_1_ID, from, "valid/text.txt"));
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    @Disabled
    public void moveResource_throws_whenToPathIsIncorrect(String to) {
        assertThrows(CloudStorageException.class, () -> resourceService.moveResource(USER_1_ID, "valid/text.txt", to));
    }

    @ParameterizedTest
    @CsvSource({
            "folder/innerfolder/, rootFile",
            "folder/innerfolder/, folder/text.txt",
    })
    public void moveResource_throws_whenMoveDirectoryToFile(String from, String to) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioService.createDirectory(buildPath(from));

        assertThrows(CloudStorageException.class, () -> resourceService.moveResource(USER_1_ID, from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "non-existent-file.txt, new-folder/newfile.txt",
            "non-existent-folder/, new-folder/",
    })
    public void moveResource_throws_whenResourceNotFound(String from, String to) {
        assertThrows(CloudStorageException.class, () -> resourceService.moveResource(USER_1_ID, from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "folder1/existing.txt, folder2/existing.txt",
            "folder1/existing-dir/, folder2/existing-dir/"
    })
    public void moveResource_throws_whenTargetAlreadyExists(String from, String to) throws Exception {
        createTestResource(buildPath(from), TestData.getRandomMultipartFile());
        createTestResource(buildPath(to), TestData.getRandomMultipartFile());

        assertThrows(CloudStorageException.class, () -> resourceService.moveResource(USER_1_ID, from, to));
    }

    @Test
    public void getDirectoryContext_returnsEmptyList_whenDirectoryIsEmpty() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String path = "folder/";
        minioService.createDirectory(buildPath(path));

        List<ResourceResponse> directoryContent = resourceService.getDirectoryContent(USER_1_ID, path);

        assertNotNull(directoryContent);
        assertThat(directoryContent).isEmpty();
    }

    @Test
    public void getDirectoryContext_throws_whenDirectoryNotFound() {
        assertThrows(CloudStorageException.class, () -> resourceService.getDirectoryContent(USER_1_ID, "folder/"));
    }

    @ParameterizedTest
    @EmptySource
    @CsvSource({
            "folder/"
    })
    public void getDirectoryContext_shouldReturnDirectoryContext(String path) throws Exception {
        MultipartFile[] multipartFiles = new MultipartFile[]{
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile("newFolder/"),
        };
        String fullPath = buildPath(path);
        minioService.createDirectory(fullPath);
        uploadContentToDirectory(fullPath, multipartFiles);

        List<ResourceResponse> directoryContent = resourceService.getDirectoryContent(USER_1_ID, path);

        assertThat(directoryContent).hasSize(3);
        assertThat(directoryContent)
                .extracting(ResourceResponse::getName)
                .contains(multipartFiles[0].getOriginalFilename(), multipartFiles[1].getOriginalFilename(), "newFolder/");
    }

    @Test
    public void searchResources_shouldReturnListOfFoundResults() throws Exception {
        String path = "folder/";
        String absolutePath = buildPath(path);
        MultipartFile[] multipartFiles = new MultipartFile[]{
                TestData.createRandomMultipartFileWithFilename("nature.jpg"),
                TestData.createRandomMultipartFileWithFilename("nato.png"),
                TestData.getRandomMultipartFile("natPat/"),
                TestData.getRandomMultipartFile("napt.txt"),
        };

        minioService.createDirectory(absolutePath);
        uploadContentToDirectory(absolutePath, multipartFiles);

        List<ResourceResponse> responses = resourceService.searchResources(USER_1_ID, "nat");

        assertThat(responses).hasSize(3);
        assertContainsOnly(responses,
                ResourceResponse::getName,
                List.of("nature.jpg", "nato.png", "natPat"),
                String::equals
        );
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    @Disabled
    public void searchResources_throws_whenPathIsNullOrEmpty(String query) {
        assertThrows(CloudStorageException.class, () -> resourceService.searchResources(USER_1_ID, query));
    }

    @ParameterizedTest
    @CsvSource({
            "nature.jpg",
            "images/nature.jpg"
    })
    void downloadResource_shouldReturnFileContent_whenResourceIsFile(String path) throws Exception {
        byte[] expectedBytes = TestData.getNewResourceContent();
        try (InputStream jpegInputStream = new ByteArrayInputStream(expectedBytes)) {
            minioService.uploadObject(buildPath(path), jpegInputStream, expectedBytes.length, "image/jpeg");
        }

        ResourceDownloadResponse downloadedResource = resourceService.downloadResource(USER_1_ID, path);

        assertNotNull(downloadedResource);
        assertNotNull(downloadedResource.getContent());
        byte[] actualBytes = downloadedResource.getContent().getInputStream().readAllBytes();

        assertThat(actualBytes).isEqualTo(expectedBytes);
    }

    @ParameterizedTest
    @CsvSource({
            "documents/",
            "images/nature/"
    })
    void downloadResource_shouldReturnZipArchive_whenResourceIsDirectoryWithContent(String directoryPath) throws Exception {
        MultipartFile[] files = new MultipartFile[]{
                TestData.getRandomMultipartFile(),
                TestData.getRandomMultipartFile(),
        };

        String fullDirectoryPath = buildPath(directoryPath);
        minioService.createDirectory(fullDirectoryPath);
        uploadContentToDirectory(fullDirectoryPath, files);

        ResourceDownloadResponse downloadedZipResponse = resourceService.downloadResource(USER_1_ID, directoryPath);
        assertNotNull(downloadedZipResponse);
        assertNotNull(downloadedZipResponse.getContent());
        InputStream downloadedZip = downloadedZipResponse.getContent().getInputStream();

        Map<String, byte[]> extractedFiles = extractZipContents(downloadedZip);

        for (MultipartFile file : files) {
            String expectedFileNameInZip = file.getOriginalFilename();
            assertThat(extractedFiles).containsKey(expectedFileNameInZip);
            assertThat(extractedFiles.get(expectedFileNameInZip)).isEqualTo(file.getBytes());
        }
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @Disabled
    void downloadResource_shouldThrowException_whenPathIsInvalid(String path) {
        assertThrows(CloudStorageException.class, () -> resourceService.downloadResource(USER_1_ID, path));
    }

    @Test
    void downloadResource_shouldThrowException_whenResourceNotFound() {
        assertThrows(CloudStorageException.class, () -> resourceService.downloadResource(USER_1_ID, "unknown/resource/"));
    }
}
