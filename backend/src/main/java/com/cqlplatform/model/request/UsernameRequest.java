package com.cqlplatform.model.request;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for share/unshare endpoints.
 */
@Data
public class UsernameRequest {
    @NotBlank
    @Size(max = 100)
    @NoXss
    private String targetUsername;

    private String currentUser;
}
