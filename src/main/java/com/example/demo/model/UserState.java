package com.example.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class UserState {

    @Id
    private Long chatId;
    private String state;
    private String foodName;
    public UserState() {}
    public UserState(long chatId) {
        this.chatId = chatId;
        this.state = null;
    }
}
