package com.benchmark.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItemDTO {

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
