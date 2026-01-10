package com.idar.optisaas.config;

import com.idar.optisaas.entity.*;
import com.idar.optisaas.model.PriceRule;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PriceMatrixRepository priceMatrixRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (branchRepository.count() > 0) {
            System.out.println(">>> La base de datos ya tiene datos. Omitiendo seed.");
            return;
        }

        System.out.println(">>> INICIANDO DATA SEEDER (GOD MODE)...");

        Branch branch = new Branch();
        branch.setName("Sucursal Central");
        branch.setAddress("Calle Falsa 123");
        branch.setSecurityPin(passwordEncoder.encode("1234"));
        Branch savedBranch = branchRepository.save(branch);

        User user = new User();
        user.setEmail("admin@opti.com");
        user.setFullName("Admin Supremo");
        user.setPassword(passwordEncoder.encode("123456"));
        user.setActive(true);
        
        UserBranchRole role = new UserBranchRole();
        role.setBranch(savedBranch);
        role.setUser(user);
        role.setRole(Role.MANAGER);
        
        user.setBranchRoles(Set.of(role));
        userRepository.save(user);

        Client client = new Client();
        client.setFullName("Cliente Victima");
        client.setEmail("victima@test.com");
        client.setPhone("555-0000");
        client.setBranchId(savedBranch.getId());
        clientRepository.save(client);

        Product product = new Product();
        product.setSku("LENS-001");
        product.setBrand("RayBan");
        product.setModel("Aviator");
        product.setType(ProductType.FRAME);
        product.setBasePrice(new BigDecimal("150.00"));
        product.setStockQuantity(10);
        product.setBranchId(savedBranch.getId());
        productRepository.save(product);

        Product treatment = new Product();
        treatment.setSku("TREAT-BLUE");
        treatment.setBrand("OptiLab");
        treatment.setModel("BlueBlock Filter");
        treatment.setType(ProductType.SERVICE);
        treatment.setBasePrice(new BigDecimal("500.00"));
        treatment.setStockQuantity(9999);
        treatment.setBranchId(savedBranch.getId());
        productRepository.save(treatment);

        PriceMatrix matrix = new PriceMatrix();
        matrix.setName("Lista General 2026");
        matrix.setActive(true);
        matrix.setBranchId(savedBranch.getId());

        PriceRule rule = new PriceRule();
        rule.setMaterial("CR39");
        rule.setMinSphere(-10.0);
        rule.setMaxSphere(10.0);
        rule.setMinCylinder(-4.0);
        rule.setMaxCylinder(0.0);
        rule.setPrice(new BigDecimal("350.00"));

        matrix.setRules(java.util.List.of(rule));
        priceMatrixRepository.save(matrix);

        System.out.println(">>> SEED COMPLETADO: Admin (123456) / Branch (1234) creados.");
    }
}