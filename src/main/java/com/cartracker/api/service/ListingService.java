package com.cartracker.api.service;

import com.cartracker.api.dto.request.ListingQuery;
import com.cartracker.api.dto.response.ListingDetailResponse;
import com.cartracker.api.dto.response.PriceHistoryResponse;
import com.cartracker.api.mapper.ListingMapper;
import com.cartracker.api.specification.ListingFilterSpecification;
import com.cartracker.common.error.exception.EntityNotFoundException;
import com.cartracker.entity.ListingEntity;
import com.cartracker.repository.ListingRepository;
import com.cartracker.scoring.PricingService;
import com.cartracker.scoring.model.FairPriceVerdict;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingService {

  private final ListingRepository listingRepository;
  private final PricingService pricingService;
  private final ListingMapper mapper = ListingMapper.INSTANCE;

  public Page<ListingEntity> search(ListingQuery query,
                                    Pageable pageable) {
    return listingRepository.findAll(
        new ListingFilterSpecification(query),
        pageable
    );
  }

  /**
   * Detail view: entity + fair-price verdict + chronological price history.
   */
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
