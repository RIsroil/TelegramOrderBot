package com.example.demo.model;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
