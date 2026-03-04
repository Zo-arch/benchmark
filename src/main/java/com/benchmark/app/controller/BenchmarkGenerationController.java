package com.benchmark.app.controller;

import com.benchmark.app.model.ItemEntity;
import com.benchmark.app.repository.ItemRepository;
import com.benchmark.app.service.SnapshotExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/benchmark/generate")
public class BenchmarkGenerationController {

    public static final String HEADER_PROCESSING_MS = "X-Processing-Ms";

    private final ItemRepository itemRepository;
    private final SnapshotExportService snapshotExportService;

    public BenchmarkGenerationController(ItemRepository itemRepository,
                                         SnapshotExportService snapshotExportService) {
        this.itemRepository = itemRepository;
        this.snapshotExportService = snapshotExportService;
    }

    @GetMapping("/proto-full")
    public ResponseEntity<Map<String, Object>> generateProtoFull() throws Exception {
        return runGeneration("protobuf-full", items -> snapshotExportService.buildProtoSnapshot(items));
    }

    @GetMapping("/sqlite-full")
    public ResponseEntity<Map<String, Object>> generateSqliteFull() throws Exception {
        return runGeneration("sqlite-full", items -> snapshotExportService.buildSqliteSnapshot(items));
    }

    @GetMapping("/json-full")
    public ResponseEntity<Map<String, Object>> generateJsonFull() throws Exception {
        return runGeneration("json-full", items -> snapshotExportService.buildJsonSnapshot(items));
    }

    @GetMapping("/proto-map")
    public ResponseEntity<Map<String, Object>> generateProtoMap() throws Exception {
        return runGeneration("protobuf-map", items -> snapshotExportService.buildMapProtoSnapshot(items));
    }

    @GetMapping("/json-map")
    public ResponseEntity<Map<String, Object>> generateJsonMap() throws Exception {
        return runGeneration("json-map", items -> snapshotExportService.buildMapJsonSnapshot(items));
    }

    private ResponseEntity<Map<String, Object>> runGeneration(String format,
                                                              GenerationFunction fn) throws Exception {
        long totalStartMs = System.currentTimeMillis();
        long dbStartMs = totalStartMs;
        List<ItemEntity> items = itemRepository.findAllByOrderByIdAsc();
        long dbLoadMs = System.currentTimeMillis() - dbStartMs;

        long serializeStartMs = System.currentTimeMillis();
        byte[] payload = fn.generate(items);
        long serializeMs = System.currentTimeMillis() - serializeStartMs;
        long totalMs = System.currentTimeMillis() - totalStartMs;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("format", format);
        body.put("items", items.size());
        body.put("payloadBytes", payload.length);
        body.put("dbLoadMs", dbLoadMs);
        body.put("serializeMs", serializeMs);
        body.put("totalMs", totalMs);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_PROCESSING_MS, String.valueOf(totalMs));
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @FunctionalInterface
    private interface GenerationFunction {
        byte[] generate(List<ItemEntity> items) throws Exception;
    }
}
