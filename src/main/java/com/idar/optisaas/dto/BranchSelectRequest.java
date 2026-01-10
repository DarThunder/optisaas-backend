package com.idar.optisaas.dto;

import lombok.Data;

@Data
public class BranchSelectRequest {
    private Long branchId;
    private String branchPin;
}