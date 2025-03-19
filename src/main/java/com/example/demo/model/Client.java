package com.example.demo.model;

import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;
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

    private boolean isBlocked = false;

    private LocalDateTime blockedUntil; // Blok tugash sanasi

    public Client(long chatId) {
        this.chatId = chatId;
    }

    public boolean isBlocked() {
        return isBlocked && blockedUntil != null && LocalDateTime.now().isBefore(blockedUntil);
    }

    public void blockForDays(int days) {
        this.isBlocked = true;
        this.blockedUntil = LocalDateTime.now().plusDays(days);
    }

    public void unblock() {
        this.isBlocked = false;
        this.blockedUntil = null;
    }
}
