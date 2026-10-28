package com.flashdrop.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data generates the implementation at startup: save, findById, findAll, delete, ...
// Package-private: only the catalog module talks to this table directly.
interface ProductRepository extends JpaRepository<Product, Long> {
}
