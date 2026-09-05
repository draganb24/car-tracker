package com.cartracker.repository;

import com.cartracker.entity.PriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, Integer> {

  @Query("select ph from PriceHistoryEntity ph where ph.listing.id in :listingIds order by ph.recordedAt asc")
  List<PriceHistoryEntity> findByListingIds(List<Integer> listingIds);

}
