package de.tum.cit.aet.usermanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.usermanagement.domain.User;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDTO(UUID userId) {

    /**
     * @return The userDTO from the user
     */
    public static UserDTO getFromEntity(User user) {
        if (user == null) {
            return null;
        }

        return new UserDTO(
                user.getUserId()
        );
    }
}
