package com.cartracker.repository;

import com.cartracker.entity.DigestStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DigestStateRepository extends JpaRepository<DigestStateEntity, String> {
}
