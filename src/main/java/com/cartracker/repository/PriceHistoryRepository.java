package com.cartracker.repository;

import com.cartracker.entity.PriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, Integer> {
}
