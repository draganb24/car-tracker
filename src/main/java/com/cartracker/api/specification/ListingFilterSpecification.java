package com.cartracker.api.specification;

import com.cartracker.api.dto.request.ListingQuery;
import com.cartracker.entity.ListingEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ListingFilterSpecification implements Specification<ListingEntity> {

  private final transient ListingQuery query;


  @Override
  public Predicate toPredicate(@NonNull Root<ListingEntity> root,
                               @NonNull CriteriaQuery<?> queryBuilder,
                               @NonNull CriteriaBuilder cb) {
    List<Predicate> predicates = new ArrayList<>();

    filterByModel(root, cb, predicates);
    filterByYearRange(root, cb, predicates);
    filterByFuelType(root, cb, predicates);
    filterByPriceRange(root, cb, predicates);

    return cb.and(predicates.toArray(new Predicate[0]));
  }

  private void filterByModel(Root<ListingEntity> root,
                             CriteriaBuilder cb,
                             List<Predicate> predicates) {
    if (query.getModel() != null && !query.getModel().isBlank()) {
      predicates.add(
          cb.like(
              cb.lower(root.get("model")),
              "%" + query.getModel().toLowerCase() + "%"
          )
      );
    }
  }

  private void filterByYearRange(Root<ListingEntity> root,
                                 CriteriaBuilder cb,
                                 List<Predicate> predicates) {
    if (query.getMinYear() != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("year"), query.getMinYear()));
    }
    if (query.getMaxYear() != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("year"), query.getMaxYear()));
    }
  }

  private void filterByFuelType(Root<ListingEntity> root,
                                CriteriaBuilder cb,
                                List<Predicate> predicates) {
    if (query.getFuelType() != null && !query.getFuelType().isBlank()) {
      predicates.add(
          cb.equal(
              cb.lower(root.get("fuelType")),
              query.getFuelType().toLowerCase()
          )
      );
    }
  }

  private void filterByPriceRange(Root<ListingEntity> root,
                                  CriteriaBuilder cb,
                                  List<Predicate> predicates) {
    if (query.getMinPrice() != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("price"), query.getMinPrice()));
    }
    if (query.getMaxPrice() != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("price"), query.getMaxPrice()));
    }
  }
}
