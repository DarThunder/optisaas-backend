package com.idar.optisaas.config;

import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ClinicalRecordRepository clinicalRecordRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PriceMatrixRepository priceMatrixRepository;
    @Autowired private LensBasePriceRepository lensBasePriceRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (branchRepository.count() > 0) {
            System.out.println(">>> La base de datos ya tiene datos. Omitiendo seed.");
            return;
        }

        System.out.println(">>> INICIANDO DATA SEEDER (GOD MODE)...");

        // 1. SUCURSAL
        Branch branch = new Branch();
        branch.setName("Sucursal Central");
        branch.setAddress("Calle Falsa 123");
        branch.setSecurityPin(passwordEncoder.encode("1234"));
        Branch savedBranch = branchRepository.save(branch);

        // 2. USUARIO
        User user = new User();
        user.setEmail("admin@opti.com");
        user.setUsername("admin");
        user.setFullName("Admin Supremo");
        user.setPassword(passwordEncoder.encode("123456"));
        user.setActive(true);
        
        UserBranchRole role = new UserBranchRole();
        role.setBranch(savedBranch);
        role.setUser(user);
        role.setRole(Role.MANAGER);
        
        user.setBranchRoles(Set.of(role));
        User savedUser = userRepository.save(user);

        // 3. CLIENTE
        Client client = new Client();
        client.setFullName("Cliente Victima");
        client.setEmail("victima@test.com");
        client.setPhone("555-0000");
        client.setBranchId(savedBranch.getId());
        Client savedClient = clientRepository.save(client);

        // 4. HISTORIAL CLÍNICO
        ClinicalRecord record = new ClinicalRecord();
        record.setBranchId(savedBranch.getId());
        record.setClient(savedClient);
        record.setOptometrist(savedUser);
        record.setDate(LocalDate.now());
        record.setSphereRight(-1.50);
        record.setCylinderRight(-0.50);
        record.setAxisRight(180);
        record.setSphereLeft(-1.75);
        record.setCylinderLeft(-0.25);
        record.setAxisLeft(175);
        record.setAdditionRight(2.00);
        record.setAdditionLeft(2.00);
        record.setPupillaryDistance(63.0); 
        record.setHeight(22.0); 
        record.setNotes("Paciente de prueba generado automáticamente.");
        clinicalRecordRepository.save(record);

        // 5. PRODUCTOS
        createProduct(savedBranch.getId(), "LENS-001", "RayBan", "Aviator", ProductType.FRAME, "150.00", null);
        createProduct(savedBranch.getId(), "TREAT-BLUE", "OptiLab", "BlueBlock Filter", ProductType.SERVICE, "500.00", "TRATAMIENTO");
        createProduct(savedBranch.getId(), "MAT-POLY", "Generic", "Policarbonato", ProductType.LENS, "450.00", "MATERIAL");

        // 6. REGLAS DE PRECIO (MATRIZ INTELIGENTE)
        PriceMatrix matrix = new PriceMatrix();
        matrix.setName("Lista General 2026");
        matrix.setActive(true);
        matrix.setBranchId(savedBranch.getId());

        List<PriceRule> rules = new ArrayList<>();

        // Regla A: Esferas Bajas (0 a 2.00) -> Precio Base incluido en el tipo de lente (o extra bajo)
        rules.add(new PriceRule("SPHERE", 0.00, 2.00, BigDecimal.ZERO, null)); 
        
        // Regla B: Esferas Medias (2.25 a 4.00) -> +$100
        rules.add(new PriceRule("SPHERE", 2.25, 4.00, new BigDecimal("100.00"), null));

        // Regla C: Esferas Altas (4.25 a 10.00) -> +$350
        rules.add(new PriceRule("SPHERE", 4.25, 10.00, new BigDecimal("350.00"), null));

        // Regla D: Cilindro Alto (> 2.00) -> +$200
        rules.add(new PriceRule("CYLINDER", 2.25, 6.00, new BigDecimal("200.00"), null));

        // Regla E: Lente Positivo (Hipermetropía) -> +$150
        rules.add(new PriceRule("POSITIVE", null, null, new BigDecimal("150.00"), null));

        matrix.setRules(rules);
        priceMatrixRepository.save(matrix);

        // 7. PRECIOS BASE DE LENTES (CONFIGURACIÓN)
        createBasePrice(savedBranch.getId(), LensDesignType.MONOFOCAL, new BigDecimal("500.00"), "Visión Sencilla");
        createBasePrice(savedBranch.getId(), LensDesignType.BIFOCAL_FLAT_TOP, new BigDecimal("800.00"), "Bifocal con línea");
        createBasePrice(savedBranch.getId(), LensDesignType.BIFOCAL_INVISIBLE, new BigDecimal("1200.00"), "Redondo Invisible (Blended)");
        createBasePrice(savedBranch.getId(), LensDesignType.PROGRESSIVE, new BigDecimal("1800.00"), "Progresivo Multifocal");

        System.out.println(">>> SEED COMPLETADO: Datos creados exitosamente.");
    }

    // Helpers para limpiar el código
    private void createBasePrice(Long branchId, LensDesignType type, BigDecimal price, String desc) {
        if (lensBasePriceRepository.findByDesignTypeAndBranchId(type, branchId).isEmpty()) {
            LensBasePrice lbp = new LensBasePrice();
            lbp.setBranchId(branchId);
            lbp.setDesignType(type);
            lbp.setPrice(price);
            lbp.setDescription(desc);
            lensBasePriceRepository.save(lbp);
        }
    }

    private void createProduct(Long branchId, String sku, String brand, String model, ProductType type, String price, String category) {
        Product p = new Product();
        p.setSku(sku);
        p.setBrand(brand);
        p.setModel(model);
        p.setType(type);
        p.setBasePrice(new BigDecimal(price));
        p.setStockQuantity(10);
        p.setBranchId(branchId);
        if (category != null) p.setCategory(category);
        productRepository.save(p);
    }
}