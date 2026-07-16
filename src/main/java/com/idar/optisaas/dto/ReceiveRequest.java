package com.idar.optisaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReceiveRequest {

    @NotEmpty(message = "Indica al menos un renglón recibido")
    @Valid
    private List<ReceiveItemRequest> items;

    @Size(max = 100)
    private String referenceCode;

    @Size(max = 500)
    private String notes;
}
