package com.flashdrop.catalog;

import com.flashdrop.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DropService {
    private final DropRepository dropRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Long createDrop(CreateDropRequest request){
        // Only the cross-module rule lives here. Drop checks its own dates, Inventory checks its own units.
        if(request.maxPerUser()> request.totalUnits()){
            throw new IllegalArgumentException("maxPerUser cannot be more than totalUnits");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product " + request.productId() + " not found"));

        Drop drop = dropRepository.save(new Drop(product, request.name(), request.price(),
                request.startsAt(), request.endsAt(), request.maxPerUser()));

        inventoryService.createStock(drop.getId(),request.totalUnits());

        return drop.getId();
    }

}
