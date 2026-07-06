package com.idar.optisaas.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CashMovementResponse {
    private Long id;
    private String type;
    private BigDecimal amount;
    private String reason;
    private String registeredByName;
    private LocalDateTime createdAt;
}