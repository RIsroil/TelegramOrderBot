package com.example.demo.repository;

import com.example.demo.model.Client;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Orders;
import org.junit.jupiter.api.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrdersRepository extends JpaRepository<Orders, Long> {
    @Query("SELECT o FROM Orders o WHERE o.orderDate BETWEEN :startDate AND :endDate")
    List<Orders> findSoldOrdersBetweenDates(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    Optional<Orders> findByChatId(Long chatId);
    List<Orders> findByStatus(OrderStatus status);
    List<Orders> findByChatIdAndStatus(Long chatId, OrderStatus status);
    List<Orders> findByOrderDetailsContaining(String productName);
    boolean existsByClientAndStatus(Client client, OrderStatus status);


}
