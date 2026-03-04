package com.benchmark.app.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SnapshotStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public SnapshotStorageService(MinioClient minioClient,
                                  @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public byte[] getObjectBytes(String objectName) {
        try (var stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao baixar objeto do MinIO: " + objectName, e);
        }
    }
}
