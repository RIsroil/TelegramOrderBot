package com.example.demo.model;

import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {
    @Id
    private Long chatId;

    private String name;
    private String phoneNumber;
    private String deliveryAddress; // Manzil
    private String pickupTime;      // Olib ketish vaqti
    private boolean awaitingAddress; // Manzil kiritish holati
    private boolean awaitingPickupTime; // Olib ketish vaqti kiritish holati

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderHistory> history = new ArrayList<>();


    public Client(Long chatId) {
        this.chatId = chatId;
    }

    public void addOrderHistory(OrderHistory orderHistory) {
        history.add(orderHistory);
    }
}
