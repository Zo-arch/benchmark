package com.benchmark.app.service;

import com.benchmark.app.model.ItemEntity;
import com.benchmark.app.repository.ItemRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class SnapshotService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    /** Nomes fixos no MinIO: sempre sobrescreve, mantém só a última versão (para front/mobile substituírem cache). */
    private static final String OBJECT_SNAPSHOT_BIN = "snapshot.bin";
    private static final String OBJECT_SNAPSHOT_SQLITE = "snapshot.sqlite";
    private static final String OBJECT_SNAPSHOT_JSON = "snapshot.json";
    private static final String OBJECT_SNAPSHOT_MAP_BIN = "snapshot-map.bin";
    private static final String OBJECT_SNAPSHOT_MAP_JSON = "snapshot-map.json";
    private static final String OBJECT_SNAPSHOT_MAP_SQLITE = "snapshot-map.sqlite";

    private final ItemRepository itemRepository;
    private final SnapshotExportService snapshotExportService;
    private final MinioClient minioClient;
    private final String bucket;

    public SnapshotService(ItemRepository itemRepository,
                           SnapshotExportService snapshotExportService,
                           MinioClient minioClient,
                           @Value("${app.minio.bucket}") String bucket) {
        this.itemRepository = itemRepository;
        this.snapshotExportService = snapshotExportService;
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void run(ApplicationArguments args) {
        long totalStartMs = System.currentTimeMillis();
        log.info("========== Início da rotina de snapshot ==========");
        logMemoria("Antes do snapshot");

        log.info("[1/5] Carregando itens do PostgreSQL...");
        long t0 = System.currentTimeMillis();
        List<ItemEntity> items = itemRepository.findAllByOrderByIdAsc();
        long loadMs = System.currentTimeMillis() - t0;
        log.info("[1/5] Itens carregados: {} em {} ms", items.size(), loadMs);

        log.info("[2/5] Verificando bucket MinIO '{}'...", bucket);
        ensureBucketExists();

        log.info("[3/5] Gerando snapshot Protobuf ({} itens)...", items.size());
        long t1 = System.currentTimeMillis();
        byte[] protoBytes = snapshotExportService.buildProtoSnapshot(items);
        long protoMs = System.currentTimeMillis() - t1;
        log.info("[3/5] Protobuf gerado: {} bytes em {} ms ({} KB)", protoBytes.length, protoMs, protoBytes.length / 1024);
        byte[] mapProtoBytes = snapshotExportService.buildMapProtoSnapshot(items);
        byte[] jsonBytes = snapshotExportService.buildJsonSnapshot(items);
        byte[] mapJsonBytes = snapshotExportService.buildMapJsonSnapshot(items);

        byte[] sqliteBytes;
        log.info("[4/5] Gerando snapshot SQLite ({} itens)...", items.size());
        long t2 = System.currentTimeMillis();
        try {
            sqliteBytes = snapshotExportService.buildSqliteSnapshot(items);
        } catch (Exception ex) {
            log.error("Falha ao gerar snapshot SQLite", ex);
            throw new RuntimeException("Snapshot SQLite falhou", ex);
        }
        long sqliteMs = System.currentTimeMillis() - t2;
        log.info("[4/5] SQLite gerado: {} bytes em {} ms ({} KB)", sqliteBytes.length, sqliteMs, sqliteBytes.length / 1024);
        byte[] mapSqliteBytes;
        try {
            mapSqliteBytes = snapshotExportService.buildMapSqliteSnapshot(items);
        } catch (Exception ex) {
            log.error("Falha ao gerar snapshot SQLite map", ex);
            throw new RuntimeException("Snapshot SQLite map falhou", ex);
        }

        logMemoria("Após gerar Protobuf e SQLite");

        log.info("[5/5] Enviando arquivos ao MinIO (bucket={})...", bucket);
        long t3 = System.currentTimeMillis();
        long uploadBinMs;
        long uploadSqliteMs;
        try {
            uploadToMinio(OBJECT_SNAPSHOT_BIN, "application/octet-stream", protoBytes);
            uploadBinMs = System.currentTimeMillis() - t3;
            log.info("[5/5] {} enviado em {} ms ({} bytes)", OBJECT_SNAPSHOT_BIN, uploadBinMs, protoBytes.length);

            long t4 = System.currentTimeMillis();
            uploadToMinio(OBJECT_SNAPSHOT_SQLITE, "application/x-sqlite3", sqliteBytes);
            uploadSqliteMs = System.currentTimeMillis() - t4;
            log.info("[5/5] {} enviado em {} ms ({} bytes)", OBJECT_SNAPSHOT_SQLITE, uploadSqliteMs, sqliteBytes.length);

            uploadToMinio(OBJECT_SNAPSHOT_JSON, "application/json", jsonBytes);
            uploadToMinio(OBJECT_SNAPSHOT_MAP_BIN, "application/octet-stream", mapProtoBytes);
            uploadToMinio(OBJECT_SNAPSHOT_MAP_JSON, "application/json", mapJsonBytes);
            uploadToMinio(OBJECT_SNAPSHOT_MAP_SQLITE, "application/x-sqlite3", mapSqliteBytes);
        } catch (Exception ex) {
            log.error("Falha ao enviar ao MinIO", ex);
            throw new RuntimeException("Upload MinIO falhou", ex);
        }

        long totalMs = System.currentTimeMillis() - totalStartMs;
        logMemoria("Após upload MinIO");
        log.info("========== Snapshot concluído ==========");
        log.info("Resumo: {} itens | total {} ms | DB {} ms | Protobuf {} ms | SQLite {} ms | upload .bin {} ms | upload .sqlite {} ms",
                items.size(), totalMs, loadMs, protoMs, sqliteMs, uploadBinMs, uploadSqliteMs);
        log.info("Objetos no MinIO: {} ({} bytes), {} ({} bytes), {} ({} bytes), {} ({} bytes), {} ({} bytes), {} ({} bytes)",
                OBJECT_SNAPSHOT_BIN, protoBytes.length,
                OBJECT_SNAPSHOT_SQLITE, sqliteBytes.length,
                OBJECT_SNAPSHOT_JSON, jsonBytes.length,
                OBJECT_SNAPSHOT_MAP_BIN, mapProtoBytes.length,
                OBJECT_SNAPSHOT_MAP_JSON, mapJsonBytes.length,
                OBJECT_SNAPSHOT_MAP_SQLITE, mapSqliteBytes.length);
    }

    private void logMemoria(String momento) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        log.info("[Memória] {} - usada: ~{} MB | heap total: {} MB | heap max: {} MB", momento, usedMb, totalMb, maxMb);
    }

    private void ensureBucketExists() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket MinIO criado: {}", bucket);
            }
        } catch (Exception ex) {
            log.error("Falha ao verificar/criar bucket MinIO", ex);
            throw new RuntimeException("MinIO bucket check failed", ex);
        }
    }

    private void uploadToMinio(String objectName, String contentType, byte[] data) throws Exception {
        try (InputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(stream, data.length, -1)
                    .contentType(contentType)
                    .build());
        }
    }
}
