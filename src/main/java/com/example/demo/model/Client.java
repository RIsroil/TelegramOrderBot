package com.example.demo.model;

import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "client", uniqueConstraints = @UniqueConstraint(columnNames = "chatId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clientId;

    @Column(unique = true, nullable = false)
    private Long chatId;

    private String name;
    private String phoneNumber;
    private String deliveryAddress;
    private String pickupTime;
    private boolean awaitingAddress;
    private boolean awaitingPickupTime;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderHistory> history = new ArrayList<>();

    public Client(Long chatId, String name, String phoneNumber) {
        this.chatId = chatId;
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public Client(Long chatId) {
        this.chatId = chatId;
    }

    public void addOrderHistory(OrderHistory orderHistory) {
        history.add(orderHistory);
    }
}
