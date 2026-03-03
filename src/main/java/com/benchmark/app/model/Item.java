package com.benchmark.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    private Long id;
    private Double valueA;
    private Double valueB;
    private String label;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Long createdAt;
    private Long updatedAt;
    private String description;
    private String code;
    private String category;
    private String status;
    private Integer count;
    private Double score;
    private String metadata;
}
