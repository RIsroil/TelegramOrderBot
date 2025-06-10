package com.example.demo.repository;

import com.example.demo.model.Client;
import com.example.demo.model.OrderHistory;
import com.example.demo.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    List<OrderHistory> findByClient(Client client);
    Optional<OrderHistory> findByClientAndOrderIndex(Client client, Long orderIndex);

    @Query("SELECT o FROM OrderHistory o WHERE o.status = :status AND o.orderDate BETWEEN :startDate AND :endDate")
    List<OrderHistory> findByStatusAndOrderDateBetween(@Param("status") OrderStatus status,
                                                           @Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    @Query("SELECT MIN(o.orderDate) FROM OrderHistory o")
    LocalDate findEarliestDate();

    @Query("SELECT DISTINCT o.client FROM OrderHistory o WHERE o.status = 'CANCELED'")
    List<Client> findClientsWithCanceledOrders();

    @Query("SELECT o.client, COUNT(o) FROM OrderHistory o " +
            "WHERE o.status = 'CANCELED' " +
            "AND o.orderDate >= :twoMonthsAgo " +
            "GROUP BY o.client " +
            "ORDER BY COUNT(o) DESC")
    List<Object[]> findClientsWithCanceledOrdersLast2Months(@Param("twoMonthsAgo") LocalDateTime twoMonthsAgo);

    List<OrderHistory> findByClientAndOrderDateAfter(Client client, LocalDateTime orderDate);
    int countByClientPhoneNumberAndStatus(String clientPhoneNumber, OrderStatus status);

    @Query("SELECT o.client.phoneNumber, COUNT(o) FROM OrderHistory o WHERE o.status = 'CANCELED' AND o.orderDate >= :from GROUP BY o.client.phoneNumber")
    List<Object[]> findCanceledOrdersCountForClients(@Param("from") LocalDateTime from);
}

