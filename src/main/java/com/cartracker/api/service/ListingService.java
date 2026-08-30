package com.cartracker.api.service;

import com.cartracker.api.dto.request.ListingQuery;
import com.cartracker.api.specification.ListingFilterSpecification;
import com.cartracker.entity.ListingEntity;
import com.cartracker.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListingService {

  private final ListingRepository listingRepository;


  public Page<ListingEntity> search(ListingQuery query,
                                    Pageable pageable) {
    return listingRepository.findAll(
        new ListingFilterSpecification(query),
        pageable
    );
  }
}
