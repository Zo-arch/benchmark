package com.benchmark.app.controller;

import com.benchmark.app.dto.ItemDTO;
import com.benchmark.app.model.ItemEntity;
import com.benchmark.app.repository.ItemRepository;
import com.benchmark.app.service.SnapshotExportService;
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

    private final ItemRepository itemRepository;
    private final SnapshotExportService snapshotExportService;

    public SnapshotDownloadController(ItemRepository itemRepository,
                                      SnapshotExportService snapshotExportService) {
        this.itemRepository = itemRepository;
        this.snapshotExportService = snapshotExportService;
    }

    /**
     * Download do snapshot em formato Protobuf (.bin).
     * Para medição de tempo, banda e processamento.
     */
    @GetMapping("/snapshot/download/bin")
    public ResponseEntity<byte[]> downloadSnapshotBin() {
        long startMs = System.currentTimeMillis();
        List<ItemEntity> items = itemRepository.findAllByOrderByIdAsc();
        byte[] body = snapshotExportService.buildProtoSnapshot(items);
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
    public ResponseEntity<byte[]> downloadSnapshotSqlite() throws Exception {
        long startMs = System.currentTimeMillis();
        List<ItemEntity> items = itemRepository.findAllByOrderByIdAsc();
        byte[] body = snapshotExportService.buildSqliteSnapshot(items);
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
