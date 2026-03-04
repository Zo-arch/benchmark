package com.benchmark.app.controller;

import com.benchmark.app.dto.ItemDTO;
import com.benchmark.app.model.ItemEntity;
import com.benchmark.app.repository.ItemRepository;
import com.benchmark.app.service.SnapshotStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SnapshotDownloadController {

    /** Header com tempo de processamento no servidor (milissegundos) para benchmark. */
    public static final String HEADER_PROCESSING_MS = "X-Processing-Ms";

    private static final String SNAPSHOT_BIN_FILENAME = "snapshot.bin";
    private static final String SNAPSHOT_SQLITE_FILENAME = "snapshot.sqlite";
    private static final String SNAPSHOT_JSON_FILENAME = "snapshot.json";
    private static final String SNAPSHOT_MAP_BIN_FILENAME = "snapshot-map.bin";
    private static final String SNAPSHOT_MAP_JSON_FILENAME = "snapshot-map.json";
    private static final String SNAPSHOT_MAP_SQLITE_FILENAME = "snapshot-map.sqlite";
    private static final String OBJECT_SNAPSHOT_BIN = "snapshot.bin";
    private static final String OBJECT_SNAPSHOT_SQLITE = "snapshot.sqlite";
    private static final String OBJECT_SNAPSHOT_JSON = "snapshot.json";
    private static final String OBJECT_SNAPSHOT_MAP_BIN = "snapshot-map.bin";
    private static final String OBJECT_SNAPSHOT_MAP_JSON = "snapshot-map.json";
    private static final String OBJECT_SNAPSHOT_MAP_SQLITE = "snapshot-map.sqlite";

    private final ItemRepository itemRepository;
    private final SnapshotStorageService snapshotStorageService;

    public SnapshotDownloadController(ItemRepository itemRepository,
                                      SnapshotStorageService snapshotStorageService) {
        this.itemRepository = itemRepository;
        this.snapshotStorageService = snapshotStorageService;
    }

    /**
     * Download do snapshot em formato Protobuf (.bin).
     * Para medição de tempo, banda e processamento.
     */
    @GetMapping("/snapshot/download/bin")
    public ResponseEntity<byte[]> downloadSnapshotBin() {
        long startMs = System.currentTimeMillis();
        byte[] body = snapshotStorageService.getObjectBytes(OBJECT_SNAPSHOT_BIN);
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", SNAPSHOT_BIN_FILENAME);
        headers.setContentLength(body.length);
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    /**
     * Download do snapshot em formato SQLite (.sqlite).
     * Para medição de tempo, banda e processamento.
     */
    @GetMapping("/snapshot/download/sqlite")
    public ResponseEntity<byte[]> downloadSnapshotSqlite() {
        long startMs = System.currentTimeMillis();
        byte[] body = snapshotStorageService.getObjectBytes(OBJECT_SNAPSHOT_SQLITE);
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-sqlite3"));
        headers.setContentDispositionFormData("attachment", SNAPSHOT_SQLITE_FILENAME);
        headers.setContentLength(body.length);
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    /**
     * Download do snapshot full em JSON pré-gerado.
     */
    @GetMapping("/snapshot/download/json")
    public ResponseEntity<byte[]> downloadSnapshotJson() {
        long startMs = System.currentTimeMillis();
        byte[] body = snapshotStorageService.getObjectBytes(OBJECT_SNAPSHOT_JSON);
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", SNAPSHOT_JSON_FILENAME);
        headers.setContentLength(body.length);
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    /**
     * Download do snapshot reduzido para mapa em Protobuf pré-gerado.
     */
    @GetMapping("/snapshot/download/map-bin")
    public ResponseEntity<byte[]> downloadMapSnapshotBin() {
        long startMs = System.currentTimeMillis();
        byte[] body = snapshotStorageService.getObjectBytes(OBJECT_SNAPSHOT_MAP_BIN);
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", SNAPSHOT_MAP_BIN_FILENAME);
        headers.setContentLength(body.length);
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    /**
     * Download do snapshot reduzido para mapa em JSON pré-gerado.
     */
    @GetMapping("/snapshot/download/map-json")
    public ResponseEntity<byte[]> downloadMapSnapshotJson() {
        long startMs = System.currentTimeMillis();
        byte[] body = snapshotStorageService.getObjectBytes(OBJECT_SNAPSHOT_MAP_JSON);
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", SNAPSHOT_MAP_JSON_FILENAME);
        headers.setContentLength(body.length);
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    /**
     * Download do snapshot reduzido para mapa em SQLite pré-gerado.
     */
    @GetMapping("/snapshot/download/map-sqlite")
    public ResponseEntity<byte[]> downloadMapSnapshotSqlite() {
        long startMs = System.currentTimeMillis();
        byte[] body = snapshotStorageService.getObjectBytes(OBJECT_SNAPSHOT_MAP_SQLITE);
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-sqlite3"));
        headers.setContentDispositionFormData("attachment", SNAPSHOT_MAP_SQLITE_FILENAME);
        headers.setContentLength(body.length);
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    /**
     * GET all itens em JSON.
     * Para medição de tempo, banda e processamento (serialização JSON).
     */
    @GetMapping("/items")
    public ResponseEntity<List<ItemDTO>> getAllItems() {
        long startMs = System.currentTimeMillis();
        List<ItemEntity> entities = itemRepository.findAllByOrderByIdAsc();
        List<ItemDTO> body = entities.stream()
                .map(SnapshotDownloadController::toDto)
                .toList();
        long processingMs = System.currentTimeMillis() - startMs;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_PROCESSING_MS, String.valueOf(processingMs));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    private static ItemDTO toDto(ItemEntity e) {
        return ItemDTO.builder()
                .id(e.getId())
                .valueA(e.getValueA())
                .valueB(e.getValueB())
                .label(e.getLabel())
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .altitude(e.getAltitude())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .description(e.getDescription())
                .code(e.getCode())
                .category(e.getCategory())
                .status(e.getStatus())
                .count(e.getCount())
                .score(e.getScore())
                .metadata(e.getMetadata())
                .build();
    }
}
