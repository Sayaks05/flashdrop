package com.flashdrop.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;


    public void createStock(Long dropId, int totalUnits) {
        Inventory inventory = new Inventory(dropId,totalUnits);
        inventoryRepository.save(inventory);
    }

}
