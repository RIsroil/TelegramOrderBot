package com.example.demo.repository;


import com.example.demo.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    @Modifying
    @Query(value = "ALTER TABLE jon_food_menu AUTO_INCREMENT = 1", nativeQuery = true)
    void resetAutoIncrement();

    @Modifying
    @Query(value = "TRUNCATE TABLE jon_food_menu", nativeQuery = true)
    void truncateTable();

    List<Menu> findByIsActive(String active);
}
