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

    @Column(name = "chat_id", nullable = false, updatable = false, insertable = false)
    private Long chatId; // Mijoz ID'si saqlanadi, lekin faqat o‘qish uchun

    @ManyToOne
    @JoinColumn(name = "chat_id", referencedColumnName = "chatId", nullable = false)
    private Client client; // Yangi qo‘shilgan bog‘liqlik

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_phone")
    private String userPhone;

    @Column(name = "order_details", columnDefinition = "TEXT", nullable = false)
    private String orderDetails;


    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    public Orders() {}

    public Orders(Client client, String userName, String userPhone, String orderDetails, Double totalPrice, LocalDateTime orderDate, OrderStatus status) {
        this.client = client;
        this.chatId = client.getChatId(); // Client obyektidan chatId olinadi
        this.userName = userName;
        this.userPhone = userPhone;
        this.orderDetails = orderDetails;
        this.totalPrice = totalPrice;
        this.orderDate = orderDate;
        this.status = status;
    }



    // GETTER VA SETTERLAR

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
        this.chatId = client.getChatId(); // Mijoz o‘zgarsa, chatId ham moslashadi
    }

    public Long getChatId() {
        return chatId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(String orderDetails) {
        this.orderDetails = orderDetails;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
