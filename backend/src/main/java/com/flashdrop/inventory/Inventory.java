package com.flashdrop.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)


public class Inventory {

    @Id
    @Column(nullable = false,name = "drop_id")
    private Long dropId;

    @Column(nullable = false,name = "total_units")
    private int totalUnits;

    @Column(nullable = false,name = "available_units")
    private  int availableUnits;

    public Inventory(Long dropId,int totalUnits){
        if (totalUnits <= 0) {
            throw new IllegalArgumentException("totalUnits must be > 0");
        }
        this.totalUnits = totalUnits;
        this.availableUnits = totalUnits;
        this.dropId = dropId;
    }
}
