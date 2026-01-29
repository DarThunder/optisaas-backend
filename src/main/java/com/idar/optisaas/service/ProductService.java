package com.idar.optisaas.service;

import com.idar.optisaas.dto.ProductRequest;
import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.Product;
import com.idar.optisaas.repository.BranchRepository;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.ProductType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private BranchRepository branchRepository;

    public List<Product> getAllProducts() {
        Long branchId = TenantContext.getCurrentBranch();
        return productRepository.findByBranchId(branchId);
    }

    @Transactional
    public Product saveProduct(ProductRequest request) {
        Long branchId = TenantContext.getCurrentBranch();
        
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        Product product;

        // 1. Crear o Editar
        if (request.getId() != null) {
            product = productRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
            
            // Seguridad: Verificar sucursal
            // Nota: Como 'branch' es @ManyToOne insertable=false en tu entidad, 
            // validamos usando el ID de la relación o branchId
            // Si tu entidad Product tiene 'private Branch branch', úsalo:
            if(!product.getBranch().getId().equals(branchId)){
                 throw new RuntimeException("No puedes editar productos de otra sucursal");
            }
        } else {
            product = new Product();
            product.setBranch(branch); // Relación JPA
            product.setBranchId(branchId); // BaseEntity ID
        }

        // 2. Mapeo de campos
        product.setBrand(request.getBrand() != null && !request.getBrand().isEmpty() ? request.getBrand() : "Genérico");
        product.setModel(request.getModel());
        product.setCategory(request.getCategory());
        product.setColor(request.getColor());
        product.setBasePrice(request.getBasePrice());
        product.setStockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0);
        
        try {
            product.setType(ProductType.valueOf(request.getType()));
        } catch (Exception e) {
            throw new RuntimeException("Tipo de producto inválido: " + request.getType());
        }

        // 3. LIMPIEZA DE DURACIÓN ("15 min" -> 15)
        if (request.getDuration() != null && !request.getDuration().isEmpty()) {
            String numericPart = request.getDuration().replaceAll("\\D+", ""); // Solo dígitos
            if (!numericPart.isEmpty()) {
                product.setDuration(Integer.parseInt(numericPart));
            } else {
                product.setDuration(null);
            }
        } else {
            product.setDuration(null);
        }

        // 4. GENERACIÓN AUTOMÁTICA DE SKU (Si viene vacío)
        if (request.getSku() == null || request.getSku().trim().isEmpty()) {
            // Usamos milisegundos para algo pseudo-único rápido
            String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
            
            if (product.getType() == ProductType.SERVICE) {
                product.setSku("SERV-" + uniqueSuffix);
            } else {
                product.setSku("PROD-" + uniqueSuffix);
            }
        } else {
            product.setSku(request.getSku());
        }

        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Long branchId = TenantContext.getCurrentBranch();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        
        if(!product.getBranch().getId().equals(branchId)){
             throw new RuntimeException("No puedes eliminar productos de otra sucursal");
        }
        
        productRepository.delete(product);
    }
}