package com.cartracker.repository;

import com.cartracker.entity.ListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<ListingEntity, Integer>, JpaSpecificationExecutor<ListingEntity> {
  Optional<ListingEntity> findByExternalId(String externalId);

  /** Distinct external IDs already stored — used by the scraper to skip detail fetches for known listings. */
  @Query("select l.externalId from ListingEntity l")
  List<String> findAllExternalIds();

  /** Active, priced listings in a comparable cohort (same model, year range, mileage bracket). */
  @Query("""
      select l from ListingEntity l
      where lower(l.model) = lower(:model)
        and l.year between :yearMin and :yearMax
        and l.mileageKm between :mileageMin and :mileageMax
        and l.isActive = true
        and l.price is not null
      """)
  List<ListingEntity> findCohort(@Param("model") String model,
                                 @Param("yearMin") int yearMin,
                                 @Param("yearMax") int yearMax,
                                 @Param("mileageMin") int mileageMin,
                                 @Param("mileageMax") int mileageMax);

  /** Active, priced listings in a comparable cohort (same model, year range), any mileage. */
  @Query("""
      select l from ListingEntity l
      where lower(l.model) = lower(:model)
        and l.year between :yearMin and :yearMax
        and l.isActive = true
        and l.price is not null
      """)
  List<ListingEntity> findCohortIgnoringMileage(@Param("model") String model,
                                                @Param("yearMin") int yearMin,
                                                @Param("yearMax") int yearMax);

  long countByIsActiveTrue();

  List<ListingEntity> findByIsActiveTrue();
}
