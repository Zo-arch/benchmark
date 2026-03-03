package com.benchmark.app.service;

import com.benchmark.app.model.ItemEntity;
import com.benchmark.app.proto.SyncProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

@Service
public class SnapshotExportService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotExportService.class);

    /**
     * Gera o snapshot em formato Protobuf (SnapshotResponse com todos os itens).
     */
    public byte[] buildProtoSnapshot(List<ItemEntity> items) {
        long ts = System.currentTimeMillis();
        String generatedAt = Instant.now().toString();

        SyncProto.SnapshotResponse.Builder builder = SyncProto.SnapshotResponse.newBuilder()
                .setSnapshotTimestamp(ts)
                .setGeneratedAt(generatedAt);

        for (ItemEntity e : items) {
            SyncProto.Item.Builder itemBuilder = SyncProto.Item.newBuilder()
                    .setId(e.getId() != null ? e.getId() : 0L)
                    .setValueA(e.getValueA() != null ? e.getValueA() : 0.0)
                    .setValueB(e.getValueB() != null ? e.getValueB() : 0.0)
                    .setLabel(e.getLabel() != null ? e.getLabel() : "")
                    .setLatitude(e.getLatitude() != null ? e.getLatitude() : 0.0)
                    .setLongitude(e.getLongitude() != null ? e.getLongitude() : 0.0)
                    .setAltitude(e.getAltitude() != null ? e.getAltitude() : 0.0)
                    .setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt() : 0L)
                    .setUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt() : 0L)
                    .setDescription(e.getDescription() != null ? e.getDescription() : "")
                    .setCode(e.getCode() != null ? e.getCode() : "")
                    .setCategory(e.getCategory() != null ? e.getCategory() : "")
                    .setStatus(e.getStatus() != null ? e.getStatus() : "")
                    .setCount(e.getCount() != null ? e.getCount() : 0)
                    .setScore(e.getScore() != null ? e.getScore() : 0.0)
                    .setMetadata(e.getMetadata() != null ? e.getMetadata() : "");
            builder.addItems(itemBuilder.build());
        }
        byte[] bytes = builder.build().toByteArray();
        log.debug("Protobuf: SnapshotResponse com {} itens serializado em {} bytes", items.size(), bytes.length);
        return bytes;
    }

    private static final int SQLITE_BATCH_SIZE = 2000;

    /**
     * Gera o snapshot em formato SQLite (arquivo .sqlite com tabela item).
     * Réplica do banco para front/mobile substituírem o cache local (.db).
     * Usa batch insert + transação única para desempenho.
     */
    public byte[] buildSqliteSnapshot(List<ItemEntity> items) throws Exception {
        loadSqliteDriver();
        java.nio.file.Path tempFile = Files.createTempFile("snapshot-", ".sqlite");
        log.debug("SQLite: arquivo temporário {}", tempFile.toAbsolutePath());
        try {
            String path = tempFile.toAbsolutePath().toString().replace("\\", "/");
            String jdbcUrl = "jdbc:sqlite:" + path;
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement stmt = conn.createStatement()) {

                stmt.execute("PRAGMA journal_mode=DELETE");
                stmt.execute("PRAGMA synchronous=OFF");
                stmt.execute("PRAGMA cache_size=-64000");
                conn.setAutoCommit(false);

                stmt.execute("""
                    CREATE TABLE item (
                        id INTEGER PRIMARY KEY,
                        value_a REAL NOT NULL,
                        value_b REAL NOT NULL,
                        label TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        altitude REAL,
                        created_at INTEGER,
                        updated_at INTEGER,
                        description TEXT,
                        code TEXT,
                        category TEXT,
                        status TEXT,
                        count INTEGER,
                        score REAL,
                        metadata TEXT
                    )
                    """);
                log.debug("SQLite: tabela item criada");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO item (id, value_a, value_b, label, latitude, longitude, altitude, created_at, updated_at, description, code, category, status, count, score, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < items.size(); i++) {
                        ItemEntity e = items.get(i);
                        ps.setLong(1, e.getId() != null ? e.getId() : 0L);
                        ps.setDouble(2, e.getValueA() != null ? e.getValueA() : 0.0);
                        ps.setDouble(3, e.getValueB() != null ? e.getValueB() : 0.0);
                        ps.setString(4, e.getLabel() != null ? e.getLabel() : "");
                        ps.setObject(5, e.getLatitude());
                        ps.setObject(6, e.getLongitude());
                        ps.setObject(7, e.getAltitude());
                        ps.setObject(8, e.getCreatedAt());
                        ps.setObject(9, e.getUpdatedAt());
                        ps.setString(10, e.getDescription() != null ? e.getDescription() : "");
                        ps.setString(11, e.getCode() != null ? e.getCode() : "");
                        ps.setString(12, e.getCategory() != null ? e.getCategory() : "");
                        ps.setString(13, e.getStatus() != null ? e.getStatus() : "");
                        ps.setObject(14, e.getCount());
                        ps.setObject(15, e.getScore());
                        ps.setString(16, e.getMetadata());
                        ps.addBatch();
                        if ((i + 1) % SQLITE_BATCH_SIZE == 0 || i == items.size() - 1) {
                            ps.executeBatch();
                        }
                    }
                }
                conn.commit();
                conn.setAutoCommit(true);
                stmt.execute("PRAGMA synchronous=FULL");
                log.debug("SQLite: {} registros inseridos (batch size {})", items.size(), SQLITE_BATCH_SIZE);
            }
            byte[] bytes = Files.readAllBytes(tempFile);
            log.debug("SQLite: arquivo lido, {} bytes", bytes.length);
            return bytes;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void loadSqliteDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver SQLite (org.sqlite.JDBC) não encontrado. Verifique a dependência sqlite-jdbc no pom.xml.", e);
        }
    }
}
