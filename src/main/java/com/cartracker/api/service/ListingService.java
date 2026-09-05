package com.cartracker.api.service;

import com.cartracker.api.dto.request.ListingQuery;
import com.cartracker.api.dto.response.ListingDetailResponse;
import com.cartracker.api.dto.response.ListingResponse;
import com.cartracker.api.dto.response.PriceHistoryResponse;
import com.cartracker.api.mapper.ListingMapper;
import com.cartracker.api.specification.ListingFilterSpecification;
import com.cartracker.common.error.exception.EntityNotFoundException;
import com.cartracker.entity.ListingEntity;
import com.cartracker.entity.PriceHistoryEntity;
import com.cartracker.repository.ListingRepository;
import com.cartracker.repository.PriceHistoryRepository;
import com.cartracker.scoring.PricingService;
import com.cartracker.scoring.model.FairPriceVerdict;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingService {

  private final ListingRepository listingRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final PricingService pricingService;
  private final ListingMapper mapper = ListingMapper.INSTANCE;

  public Page<ListingEntity> search(ListingQuery query,
                                    Pageable pageable) {
    return listingRepository.findAll(
        new ListingFilterSpecification(query),
        pageable
    );
  }

  @Transactional(readOnly = true)
  public Page<ListingResponse> searchList(Page<ListingEntity> page) {
    List<ListingEntity> listings = page.getContent();
    List<Integer> ids = listings.stream()
        .map(ListingEntity::getId)
        .toList();

    List<PriceHistoryEntity> allHistory = ids.isEmpty()
        ? List.of()
        : priceHistoryRepository.findByListingIds(ids);

    Map<Integer, List<PriceHistoryEntity>> byListing = allHistory.stream()
        .collect(Collectors.groupingBy(h -> h.getListing().getId()));

    List<ListingResponse> content = listings.stream()
        .map(l -> {
          FairPriceVerdict verdict = pricingService.score(l);
          List<PriceHistoryResponse> history = byListing.getOrDefault(
              l.getId(), List.of()).stream()
              .map(mapper::fromPriceHistory)
              .toList();
          return ListingResponse.builder()
              .id(l.getId())
              .externalId(l.getExternalId())
              .source(l.getSource())
              .title(l.getTitle())
              .brand(l.getBrand())
              .model(l.getModel())
              .price(l.getPrice())
              .currency(l.getCurrency())
              .year(l.getYear())
              .mileageKm(l.getMileageKm())
              .fuelType(l.getFuelType())
              .location(l.getLocation())
              .url(l.getUrl())
              .firstSeenAt(l.getFirstSeenAt())
              .lastSeenAt(l.getLastSeenAt())
              .isActive(l.getIsActive())
              .priceHistory(history)
              .priceLabel(verdict != null ? verdict.priceLabel() : null)
              .cohortAverage(verdict != null ? verdict.cohortAverage() : null)
              .cohortMedian(verdict != null ? verdict.cohortMedian() : null)
              .cohortCount(verdict != null ? verdict.cohortCount() : 0)
              .deltaPercent(verdict != null ? verdict.deltaPercent() : null)
              .goodDeal(verdict != null && verdict.goodDeal())
              .mileageBracketKm(verdict != null ? verdict.mileageBracketKm() : 0)
              .build();
        })
        .toList();

    return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
  }

  @Transactional(readOnly = true)
  public ListingDetailResponse detail(Integer id) {
    ListingEntity listing = listingRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            ListingEntity.class, "Listing not found: " + id));
    Hibernate.initialize(listing.getPriceHistory());
    FairPriceVerdict verdict = pricingService.score(listing);
    List<PriceHistoryResponse> history =
        listing.getPriceHistory().stream()
            .map(mapper::fromPriceHistory)
            .toList();
    return mapper.fromEntityToDetail(
        listing,
        verdict,
        history
    );
  }
}
