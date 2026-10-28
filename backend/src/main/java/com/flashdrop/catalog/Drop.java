package com.flashdrop.catalog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;


@Entity
@Table(name = "drops")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Drop {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private  Product product;

    @Column(nullable = false,length = 200)
    private String name;

    @Column(nullable = false, precision = 10,scale = 2)
    private BigDecimal price;

    @Column(nullable = false,name = "starts_at")
    private Instant startsAt;

    @Column(nullable = false,name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false,name = "max_per_user")
    private int maxPerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DropStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Drop(Product product,String name,BigDecimal price,Instant startsAt,Instant endsAt,int maxPerUser){
        this.product = product;
        this.name = name;
        this.price = price;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxPerUser = maxPerUser;
        this.status = DropStatus.SCHEDULED;
    }
    public void goLive() {
        if(status!=DropStatus.SCHEDULED){
            throw new IllegalStateException("Drop " +id+ " can't go live from " + status);
        }
        this.status=DropStatus.LIVE;
    }

    public void end() {
        if(status!=DropStatus.LIVE){
            throw new IllegalStateException("Drop " +id+ " can't end from " + status);
        }
        this.status=DropStatus.ENDED;
    }

    public void cancel() {
        if(status==DropStatus.CANCELLED){
            return;
        }
        if(status==DropStatus.ENDED){
            throw new IllegalStateException("Drop " +id+ " can't be cancelled from " + status);
        }
        this.status=DropStatus.CANCELLED;
    }
}
