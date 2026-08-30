package com.cartracker.repository;

import com.cartracker.entity.ListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<ListingEntity, Integer>, JpaSpecificationExecutor<ListingEntity> {
  Optional<ListingEntity> findByExternalId(String externalId);
}
