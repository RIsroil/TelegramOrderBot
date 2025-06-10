package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "orders")
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, updatable = false, insertable = false)
    private Long chatId;

    @ManyToOne
    @JoinColumn(name = "chat_id", referencedColumnName = "chatId", nullable = false)
    private Client client;

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
        this.chatId = client.getChatId();
        this.userName = userName;
        this.userPhone = userPhone;
        this.orderDetails = orderDetails;
        this.totalPrice = totalPrice;
        this.orderDate = orderDate;
        this.status = status;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
        this.chatId = client.getChatId();
    }
}
