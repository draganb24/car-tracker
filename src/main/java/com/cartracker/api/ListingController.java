package com.cartracker.api;

import com.cartracker.api.dto.request.ListingQuery;
import com.cartracker.api.dto.response.ListingDetailResponse;
import com.cartracker.api.dto.response.ListingResponse;
import com.cartracker.api.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/listings")
public class ListingController {

  private final ListingService listingService;

  @GetMapping
  public Page<ListingResponse> list(@Valid @ModelAttribute ListingQuery query,
                                    Pageable pageable) {
    return listingService.searchList(listingService.search(
            query,
            pageable
        )
    );
  }

  @GetMapping("/{id}")
  public ListingDetailResponse detail(@PathVariable Integer id) {
    return listingService.detail(id);
  }
}
