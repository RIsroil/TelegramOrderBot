package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
public class UserOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private LocalDateTime orderDate;

    @ElementCollection
    private Map<Long, Integer> orderDetails; // foodId -> quantity

    // Getter va Setterlar


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public Map<Long, Integer> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(Map<Long, Integer> orderDetails) {
        this.orderDetails = orderDetails;
    }
}
