package com.cartracker.api.mapper;

import com.cartracker.api.dto.ListingDto;
import com.cartracker.domain.Listing;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ListingMapper {

    ListingMapper INSTANCE = Mappers.getMapper(ListingMapper.class);

    ListingDto toDto(Listing listing);
}
