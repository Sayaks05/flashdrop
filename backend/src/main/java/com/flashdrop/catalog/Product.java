package com.flashdrop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * What is being sold. Maps to the {@code products} table from V1__create_core_schema.sql.
 */
@Entity
@Table(name = "products")
@Getter
// JPA needs a no-args constructor to build objects from rows. PROTECTED keeps application
// code from calling it and creating a half-empty Product.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    // IDENTITY matches "GENERATED ALWAYS AS IDENTITY": Postgres assigns the id on insert.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String brand;

    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // The database fills this in (DEFAULT now()), so Hibernate must never write it.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Product(String name, String brand, String description, String imageUrl) {
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.imageUrl = imageUrl;
    }
}
