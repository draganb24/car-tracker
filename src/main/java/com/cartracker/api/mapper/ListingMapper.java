package com.cartracker.api.mapper;

import com.cartracker.api.dto.response.ListingResponse;
import com.cartracker.entity.ListingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ListingMapper {
    ListingMapper INSTANCE = Mappers.getMapper(ListingMapper.class);

    ListingResponse fromEntityToResponse(ListingEntity listing);
}
