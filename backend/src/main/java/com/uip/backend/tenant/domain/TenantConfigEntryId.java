package com.uip.backend.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TenantConfigEntryId implements Serializable {
    private String tenantId;
    private String configKey;
}
