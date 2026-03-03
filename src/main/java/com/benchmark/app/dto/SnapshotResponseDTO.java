package com.benchmark.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotResponseDTO {

    private Long snapshotTimestamp;
    private String generatedAt;
    private List<ItemDTO> items;
}
