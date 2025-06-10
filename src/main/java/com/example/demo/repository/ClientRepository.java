package com.example.demo.repository;

import com.example.demo.model.Client;
import com.example.demo.model.OrderHistory;
import com.example.demo.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByChatId(Long chatId);
    Optional<Client> findByPhoneNumber(String phoneNumber);
    @Query("SELECT o FROM OrderHistory o WHERE o.status = :status AND o.orderDate BETWEEN :startDate AND :endDate")
    List<OrderHistory> findByStatusAndOrderDateBetween(@Param("status") OrderStatus status,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DISTINCT o.client FROM OrderHistory o WHERE o.orderDate >= :from")
    List<Client> findClientsWithOrdersLast3Months(@Param("from") LocalDateTime from);
    List<Client> findByIsBlockedTrue();
}
