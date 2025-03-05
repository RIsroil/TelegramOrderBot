package com.example.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

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

    public Client(Long chatId) {
        this.chatId = chatId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public boolean isAwaitingAddress() {
        return awaitingAddress;
    }

    public void setAwaitingAddress(boolean awaitingAddress) {
        this.awaitingAddress = awaitingAddress;
    }

    public boolean isAwaitingPickupTime() {
        return awaitingPickupTime;
    }

    public void setAwaitingPickupTime(boolean awaitingPickupTime) {
        this.awaitingPickupTime = awaitingPickupTime;
    }
}
