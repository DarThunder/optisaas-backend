package com.idar.optisaas.service;

import com.idar.optisaas.entity.Product;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    @Autowired private ProductRepository productRepository;

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public Product saveProduct(Product product) {
        // --- VALIDACIÓN DE SKU DUPLICADO ---
        // Si el producto tiene SKU, verificamos que no exista ya en OTRO producto
        if (product.getSku() != null && !product.getSku().isBlank()) {
            boolean exists = productRepository.existsBySku(product.getSku());
            
            // Si es nuevo (ID null) y existe -> ERROR
            if (product.getId() == null && exists) {
                throw new RuntimeException("El código SKU '" + product.getSku() + "' ya está registrado.");
            }
            
            // Si es edición, verificamos que el SKU no pertenezca a OTRO ID diferente
            if (product.getId() != null) {
                Product existingWithSku = productRepository.findById(product.getId()).orElse(null);
                // Si cambiamos el SKU y el nuevo ya está ocupado...
                if (existingWithSku != null && !existingWithSku.getSku().equals(product.getSku()) && exists) {
                    throw new RuntimeException("El código SKU '" + product.getSku() + "' ya pertenece a otro producto.");
                }
            }
        }

        // Lógica de Guardado (Igual que antes)
        if (product.getId() == null) {
            product.setBranchId(TenantContext.getCurrentBranch());
            return productRepository.save(product);
        } else {
            Product existingProduct = productRepository.findById(product.getId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            if (!existingProduct.getBranchId().equals(TenantContext.getCurrentBranch())) {
                throw new RuntimeException("No tienes permiso para editar este producto");
            }

            // Actualizamos campos
            existingProduct.setBrand(product.getBrand());
            existingProduct.setModel(product.getModel());
            existingProduct.setSku(product.getSku());
            existingProduct.setColor(product.getColor());
            existingProduct.setCategory(product.getCategory());
            existingProduct.setDuration(product.getDuration());
            existingProduct.setType(product.getType());
            existingProduct.setBasePrice(product.getBasePrice());
            existingProduct.setStockQuantity(product.getStockQuantity());
            
            return productRepository.save(existingProduct);
        }
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (!product.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso para eliminar este producto");
        }
        productRepository.delete(product);
    }
}