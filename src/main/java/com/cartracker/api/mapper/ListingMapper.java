package com.cartracker.api.mapper;

import com.cartracker.api.dto.response.ListingDetailResponse;
import com.cartracker.api.dto.response.ListingResponse;
import com.cartracker.api.dto.response.PriceHistoryResponse;
import com.cartracker.entity.ListingEntity;
import com.cartracker.entity.PriceHistoryEntity;
import com.cartracker.scoring.model.FairPriceVerdict;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ListingMapper {
  ListingMapper INSTANCE = Mappers.getMapper(ListingMapper.class);

  ListingResponse fromEntityToResponse(ListingEntity listing);

  @Mapping(target = "id", source = "listing.id")
  @Mapping(target = "externalId", source = "listing.externalId")
  @Mapping(target = "source", source = "listing.source")
  @Mapping(target = "title", source = "listing.title")
  @Mapping(target = "brand", source = "listing.brand")
  @Mapping(target = "model", source = "listing.model")
  @Mapping(target = "price", source = "listing.price")
  @Mapping(target = "currency", source = "listing.currency")
  @Mapping(target = "year", source = "listing.year")
  @Mapping(target = "mileageKm", source = "listing.mileageKm")
  @Mapping(target = "fuelType", source = "listing.fuelType")
  @Mapping(target = "location", source = "listing.location")
  @Mapping(target = "url", source = "listing.url")
  @Mapping(target = "firstSeenAt", source = "listing.firstSeenAt")
  @Mapping(target = "lastSeenAt", source = "listing.lastSeenAt")
  @Mapping(target = "isActive", source = "listing.isActive")
  @Mapping(target = "fairPrice", source = "fairPrice")
  @Mapping(target = "priceHistory", source = "priceHistory")
  ListingDetailResponse fromEntityToDetail(ListingEntity listing,
                                           FairPriceVerdict fairPrice,
                                           List<PriceHistoryResponse> priceHistory);

  PriceHistoryResponse fromPriceHistory(PriceHistoryEntity ph);
}
