package de.tum.cit.aet.util.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageDTO(@Min(1) int pageSize, @Min(0) int pageNumber) {}
