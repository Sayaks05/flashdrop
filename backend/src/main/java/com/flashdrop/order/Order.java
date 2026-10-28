package com.flashdrop.order;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;


@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "drop_id")
    private Long dropId;

    @Column(nullable = false,name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false,name = "unit_price",precision = 10 ,scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length=20 )
    private OrderStatus status;

    @Column(name = "created_at",nullable = false,insertable = false,updatable = false)
    private Instant createdAt;

    @Column(nullable = false, name = "updated_at")
    @UpdateTimestamp()
    private Instant updatedAt;

    public Order(Long dropId, Long userId, int quantity, BigDecimal unitPrice){
            this.dropId = dropId;
            this.userId = userId;
            this.quantity= quantity;
            this.unitPrice = unitPrice;
            this.status = OrderStatus.PENDING_PAYMENT;

    }

}
