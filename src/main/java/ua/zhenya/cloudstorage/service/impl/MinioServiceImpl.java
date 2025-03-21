package ua.zhenya.cloudstorage.service.impl;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.zhenya.cloudstorage.properties.MinioProperties;
import ua.zhenya.cloudstorage.service.MinioService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void init() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        createBucket();
    }

    @Override
    public void createDirectory(String fullPath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioProperties.getBucketName())
                .object(fullPath)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .build());
    }

    public GetObjectResponse getObject(String fullPath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucketName())
                .object(fullPath)
                .build());
    }

    public void deleteObject(String fullPath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minioProperties.getBucketName())
                .object(fullPath)
                .build());
    }

    public Iterable<Result<DeleteError>> deleteObjects(Iterable<DeleteObject> objects) {
        return minioClient.removeObjects(RemoveObjectsArgs.builder()
                .bucket(minioProperties.getBucketName())
                .objects(objects)
                .build());
    }

    public ObjectWriteResponse uploadObject(String path, InputStream inputStream, long size, String contentType) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioProperties.getBucketName())
                .object(path)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build());
    }

    public StatObjectResponse getObjectInfo(String path) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(minioProperties.getBucketName())
                .object(path)
                .build());
    }

    public Iterable<Result<Item>> listObjects(String path, boolean recursive) {
        return minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(minioProperties.getBucketName())
                .prefix(path)
                .recursive(recursive)
                .build());
    }

    public void copyObject(String sourceObjectPath, String targetObjectPath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioClient.copyObject(CopyObjectArgs.builder()
                .source(CopySource.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(sourceObjectPath)
                        .build())
                .bucket(minioProperties.getBucketName())
                .object(targetObjectPath)
                .build());
    }

    public void moveObject(String sourceObjectPath, String targetObjectPath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        copyObject(sourceObjectPath, targetObjectPath);
        deleteObject(sourceObjectPath);
    }

    @Override
    public boolean objectExists(String path) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(path)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createBucket() throws
            ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String bucketName = minioProperties.getBucketName();

        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }
}
