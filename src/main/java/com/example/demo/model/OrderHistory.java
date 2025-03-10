package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class OrderHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private Long orderId;
    private String orderDetails;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime orderDate;

    public OrderHistory(Client client, Long orderId, String orderDetails, OrderStatus status) {
        this.client = client;
        this.orderId = orderId;
        this.orderDetails = orderDetails;
        this.status = status;
        this.orderDate = LocalDateTime.now();
    }

    public OrderHistory() {

    }

    public OrderStatus getStatus() {
        return status;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}
