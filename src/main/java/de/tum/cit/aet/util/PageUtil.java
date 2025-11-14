package de.tum.cit.aet.util;

import de.tum.cit.aet.util.dto.PageDTO;
import de.tum.cit.aet.util.dto.SortDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.data.domain.PageRequest;

import java.util.Set;

/**
 * Utility class for creating {@link PageRequest} objects with optional sorting,
 * based on predefined sets of sortable fields.
 */
public class PageUtil {

    /**
     * Enum for grouping valid sortable column names for different pageable use
     * cases.
     * Helps ensure only supported fields are passed to sorting logic.
     */
    @Getter
    public enum ColumnMapping {
        /**
         * Sortable columns
         * Following is just an example and needs to be adjusted to the real implementation then.
         */
        SORTABLE_USER_COLUMNS(Set.of("name", "age", "address", "email"));

        private final Set<String> sortableColumns;

        ColumnMapping(Set<String> sortableColumns) {
            this.sortableColumns = sortableColumns;
        }
    }

    /**
     * Creates a {@link PageRequest} object for pagination with optional sorting.
     *
     * @param pageDTO       the pagination information (page number and size)
     * @param sortDTO       the sorting configuration (field and direction)
     * @param columnMapping the enum containing allowed sortable columns
     * @param applySorting  flag to determine whether sorting should be applied
     * @return a configured {@link PageRequest} with or without sorting
     */
    @NotNull
    public static PageRequest createPageRequest(PageDTO pageDTO, SortDTO sortDTO, ColumnMapping columnMapping, boolean applySorting) {
        if (!applySorting) {
            return PageRequest.of(pageDTO.pageNumber(), pageDTO.pageSize());
        } else {
            return PageRequest.of(pageDTO.pageNumber(), pageDTO.pageSize(), sortDTO.toSpringSort(columnMapping.getSortableColumns()));
        }
    }
}
