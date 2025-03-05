package com.example.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class UserState {

    @Id
    private Long chatId;

    private String state; // qaysi bosqichda (e.g., "WAITING_FOR_NAME", "WAITING_FOR_PRICE")

    private String foodName; // vaqtinchalik menyu uchun ovqat nomi

    public UserState() {
    }

    public UserState(long chatId) {
        this.chatId = chatId;
        this.state = null; // Default qiymat
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }
}
