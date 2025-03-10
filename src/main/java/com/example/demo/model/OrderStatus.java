package com.example.demo.model;

public enum OrderStatus {
    ORDERED, // Buyurtma berildi, lekin mijoz hali kelmadi
    SOLD,     // Buyurtma sotildi, mijoz olib ketdi
    CANCELED
}