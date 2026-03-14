package com.cqlplatform.model.request;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for ownership transfer endpoints.
 */
@Data
public class TransferOwnershipRequest {
    @NotBlank
    @Size(max = 100)
    @NoXss
    private String newOwner;
}
