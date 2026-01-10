package com.idar.optisaas.service;

import com.idar.optisaas.entity.*;
import com.idar.optisaas.model.PriceRule;
import com.idar.optisaas.dto.*;
import com.idar.optisaas.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ClinicalService {

    @Autowired private ClinicalRecordRepository clinicalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PriceMatrixRepository priceMatrixRepository;
    @Autowired private ProductRepository productRepository;

    @Transactional
    public ClinicalRecord createRecord(ClinicalRecordRequest request, String optometristEmail) {
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        User optometrist = userRepository.findByEmail(optometristEmail)
                .orElseThrow(() -> new RuntimeException("Optometrista no encontrado"));

        ClinicalRecord record = new ClinicalRecord();
        record.setClient(client);
        record.setOptometrist(optometrist);
        
        record.setSphereRight(request.getSphereRight());
        record.setSphereLeft(request.getSphereLeft());
        record.setCylinderRight(request.getCylinderRight());
        record.setCylinderLeft(request.getCylinderLeft());
        record.setAxisRight(request.getAxisRight());
        record.setAxisLeft(request.getAxisLeft());
        record.setAddition(request.getAddition());
        record.setPupillaryDistance(request.getPupillaryDistance());
        record.setNotes(request.getNotes());

        return clinicalRepository.save(record);
    }

    public PriceResponse calculatePrice(PriceCalculationRequest request) {
        ClinicalRecord record = clinicalRepository.findById(request.getClinicalRecordId())
                .orElseThrow(() -> new RuntimeException("Receta no encontrada"));

        Long currentBranchId = com.idar.optisaas.security.TenantContext.getCurrentBranch();

        PriceMatrix matrix = priceMatrixRepository.findByActiveTrueAndBranchId(com.idar.optisaas.security.TenantContext.getCurrentBranch())
                .orElseThrow(() -> new RuntimeException("No hay lista de precios activa para esta sucursal"));

        BigDecimal rightEyePrice = findPriceForEye(matrix, record.getSphereRight(), record.getCylinderRight(), request.getMaterial());
        BigDecimal leftEyePrice = findPriceForEye(matrix, record.getSphereLeft(), record.getCylinderLeft(), request.getMaterial());

        BigDecimal treatmentPrice = BigDecimal.ZERO; 
        if ("BlueBlock".equals(request.getTreatment())) {
            Product treatmentProduct = productRepository.findBySkuAndBranchId("TREAT-BLUE", currentBranchId)
                .orElseThrow(() -> new RuntimeException("El tratamiento 'BlueBlock' no está configurado como producto (SKU: TREAT-BLUE)"));
            
            treatmentPrice = treatmentProduct.getBasePrice();
        }

        BigDecimal total = rightEyePrice.add(leftEyePrice).add(treatmentPrice);

        PriceResponse response = new PriceResponse();
        response.setCalculatedPrice(total);
        response.setBreakdown(String.format("OD: $%s + OI: $%s + Tratamiento: $%s", rightEyePrice, leftEyePrice, treatmentPrice));
        
        return response;
    }

    private BigDecimal findPriceForEye(PriceMatrix matrix, Double sphere, Double cylinder, String material) {
        if (sphere == null || cylinder == null) return BigDecimal.ZERO;

        Optional<PriceRule> rule = matrix.getRules().stream()
            .filter(r -> r.getMaterial().equalsIgnoreCase(material))
            .filter(r -> sphere >= r.getMinSphere() && sphere <= r.getMaxSphere())
            .filter(r -> cylinder >= r.getMinCylinder() && cylinder <= r.getMaxCylinder())
            .findFirst();

        return rule.map(PriceRule::getPrice).orElseThrow(() -> 
            new RuntimeException("No se encontró precio para graduación: Esfera " + sphere + ", Cilindro " + cylinder));
    }
}