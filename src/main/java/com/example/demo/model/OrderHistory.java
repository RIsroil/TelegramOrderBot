package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", referencedColumnName = "clientId", nullable = false) // ✅ Endi clientId ga bog‘landi
    private Client client;

    private Long orderIndex;
    private String orderDetails;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime orderDate;
    private double totalPrice;

    public OrderHistory(Client client, Long orderIndex, String orderDetails, OrderStatus status, double totalPrice) {
        this.client = client;
        this.orderIndex = orderIndex;
        this.orderDetails = orderDetails;
        this.status = status;
        this.orderDate = LocalDateTime.now();
        this.totalPrice = totalPrice;
    }

    public OrderHistory() {}

    public OrderHistory(Client client, Long id, String string, OrderStatus orderStatus) {
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public Long getOrderIndex() {
        return orderIndex;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setOrderIndex(Long orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(String orderDetails) {
        this.orderDetails = orderDetails;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }
}
