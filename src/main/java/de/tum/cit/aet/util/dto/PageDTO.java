package de.tum.cit.aet.util.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

/**
 * Pagination parameters for paginated API requests.
 *
 * @param pageSize   number of items per page (minimum 1)
 * @param pageNumber zero-based page index (minimum 0)
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageDTO(@Min(1) int pageSize, @Min(0) int pageNumber) {}
