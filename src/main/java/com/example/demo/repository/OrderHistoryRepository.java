package com.example.demo.repository;

import com.example.demo.model.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {
    OrderHistory findByOrderId(Long orderId); // Buyurtma ID bo‘yicha tarixni topish
    List<OrderHistory> findByClient_ChatId(Long chatId); // Mijozning barcha buyurtmalarini topish
}

