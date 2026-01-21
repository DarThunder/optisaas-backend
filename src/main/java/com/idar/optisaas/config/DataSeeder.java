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
import java.time.LocalDate; // Importante para la fecha
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ClinicalRecordRepository clinicalRecordRepository; // Nuevo repo
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

        // 1. SUCURSAL
        Branch branch = new Branch();
        branch.setName("Sucursal Central");
        branch.setAddress("Calle Falsa 123");
        branch.setSecurityPin(passwordEncoder.encode("1234"));
        Branch savedBranch = branchRepository.save(branch);

        // 2. USUARIO ADMIN / OPTOMETRISTA
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

        // 4. HISTORIAL CLÍNICO (NUEVO: Para que funcione el POS)
        ClinicalRecord record = new ClinicalRecord();
        record.setBranchId(savedBranch.getId());
        record.setClient(savedClient);      // Relación Objeto
        record.setOptometrist(savedUser);   // Relación Objeto
        record.setDate(LocalDate.now());    // Fecha obligatoria (LocalDate)
        
        // Rx Lejos (Miopía con Astigmatismo)
        record.setSphereRight(-1.50);
        record.setCylinderRight(-0.50);
        record.setAxisRight(180);
        
        record.setSphereLeft(-1.75);
        record.setCylinderLeft(-0.25);
        record.setAxisLeft(175);

        // Adición (Para probar Progresivos/Bifocales)
        record.setAdditionRight(2.00);
        record.setAdditionLeft(2.00);

        // Medidas Físicas (Double, no String)
        record.setPupillaryDistance(63.0); 
        record.setHeight(22.0); 
        
        record.setNotes("Paciente de prueba generado automáticamente.");
        clinicalRecordRepository.save(record);

        // 5. PRODUCTOS
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

        // 6. REGLAS DE PRECIO (Legacy)
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

        System.out.println(">>> SEED COMPLETADO: Datos creados exitosamente.");
    }
}