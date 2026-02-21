package com.cqlplatform.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class OktaUserInfo {
    private String sub;
    private String email;
    private String preferredUsername;
    private String name;
}
