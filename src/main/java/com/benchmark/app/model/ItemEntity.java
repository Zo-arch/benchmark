package com.benchmark.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item")
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "value_a", nullable = false)
    private Double valueA;

    @Column(name = "value_b", nullable = false)
    private Double valueB;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "altitude")
    private Double altitude;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "count")
    private Integer count;

    @Column(name = "score")
    private Double score;

    @Column(name = "metadata", length = 2000)
    private String metadata;
}
