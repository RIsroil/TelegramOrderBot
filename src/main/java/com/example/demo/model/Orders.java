package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Entity
@Table(name = "orders")
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "order_details", columnDefinition = "TEXT", nullable = false)
    private String orderDetails;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    // Default constructor for JPA
    public Orders() {}

    public Orders(Long chatId, Map<Long, Integer> userBasket, Double totalPrice, String orderDate) {
        this.chatId = chatId;
        this.orderDetails = formatOrderDetails(userBasket); // Basketni stringga aylantiramiz
        this.totalPrice = totalPrice;
        this.orderDate = LocalDateTime.parse(orderDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatOrderDetails(Map<Long, Integer> userBasket) {
        StringBuilder details = new StringBuilder();
        userBasket.forEach((foodId, quantity) -> details.append("Food ID: ")
                .append(foodId)
                .append(", Quantity: ")
                .append(quantity)
                .append("\n"));
        return details.toString();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getOrderDetails() {
        return orderDetails;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }
}
