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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PriceMatrixRepository priceMatrixRepository;
    @Autowired private LensBasePriceRepository lensBasePriceRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (branchRepository.count() > 0) {
            ensureSurfacingProductExists();
            System.out.println(">>> El sistema ya tiene datos base. Omitiendo carga inicial completa.");
            return;
        }

        System.out.println(">>> INICIANDO CARGA DE DATOS INICIALES (MOGAR)...");

        // 1. SUCURSAL
        Branch branch = new Branch();
        branch.setName("Óptica Mogar - Matriz");
        branch.setAddress("Centro");
        branch.setSecurityPin(passwordEncoder.encode("1234")); 
        Branch savedBranch = branchRepository.save(branch);

        // 2. USUARIO ADMIN
        User user = new User();
        user.setEmail("admin@mogar.com");
        user.setUsername("admin");
        user.setFullName("Administrador");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setActive(true);
        user.setQuickPin("1234");
        
        UserBranchRole role = new UserBranchRole();
        role.setBranch(savedBranch);
        role.setUser(user);
        role.setRole(Role.MANAGER);
        
        user.setBranchRoles(Set.of(role));
        userRepository.save(user);

        // 3. CLIENTE DE PRUEBA
        Client client = new Client();
        client.setFullName("Cliente Mostrador");
        client.setEmail("cliente@ejemplo.com");
        client.setPhone("555-0000");
        client.setBranchId(savedBranch.getId());
        clientRepository.save(client);

        // 4. CONFIGURACIÓN DE PRECIOS
        createBasePrice(savedBranch.getId(), LensDesignType.MONOFOCAL, new BigDecimal("550.00"), "Monofocal Paquete 1");
        createBasePrice(savedBranch.getId(), LensDesignType.BIFOCAL_FLAT_TOP, new BigDecimal("700.00"), "Bifocal Flat Top Blanco");
        createBasePrice(savedBranch.getId(), LensDesignType.PROGRESSIVE, new BigDecimal("1300.00"), "Progresivo Blanco");
        createBasePrice(savedBranch.getId(), LensDesignType.BIFOCAL_INVISIBLE, new BigDecimal("1000.00"), "Bifocal Invisible");

        createProduct(savedBranch.getId(), "TRAT-AR", "Mogar", "Antirreflejante (AR)", ProductType.ACCESSORY, "150.00", "Tratamiento");
        createProduct(savedBranch.getId(), "TRAT-BLUE", "Mogar", "Filtro Blue Ray", ProductType.ACCESSORY, "350.00", "Tratamiento");
        createProduct(savedBranch.getId(), "TRAT-FOTO-AR", "Mogar", "Fotocromático con AR", ProductType.ACCESSORY, "600.00", "Tratamiento");
        createProduct(savedBranch.getId(), "ACC-GOTAS", "Renu", "Gotas Lubricantes", ProductType.ACCESSORY, "120.00", "General");   

        ensureSurfacingProductExists(savedBranch.getId());

        // D. MATRIZ DE REGLAS (Corrección de branch_id)
        PriceMatrix matrix = new PriceMatrix();
        matrix.setName("Reglas de Sobreprecio (Tallado)");
        matrix.setActive(true);
        matrix.setBranchId(savedBranch.getId());

        List<PriceRule> rules = new ArrayList<>();

        // Regla 1
        PriceRule r1 = new PriceRule();
        r1.setConditionType("SPHERE"); r1.setMinVal(3.25); r1.setMaxVal(6.00); r1.setAdjustment(new BigDecimal("250.00"));
        r1.setBranchId(savedBranch.getId()); // <--- ESTO FALTABA
        r1.setMatrix(matrix);
        rules.add(r1);

        // Regla 2
        PriceRule r2 = new PriceRule();
        r2.setConditionType("SPHERE"); r2.setMinVal(6.25); r2.setMaxVal(20.00); r2.setAdjustment(new BigDecimal("500.00"));
        r2.setBranchId(savedBranch.getId()); // <--- ESTO FALTABA
        r2.setMatrix(matrix);
        rules.add(r2);

        // Regla 3
        PriceRule r3 = new PriceRule();
        r3.setConditionType("CYLINDER"); r3.setMinVal(2.25); r3.setMaxVal(6.00); r3.setAdjustment(new BigDecimal("250.00"));
        r3.setBranchId(savedBranch.getId()); // <--- ESTO FALTABA
        r3.setMatrix(matrix);
        rules.add(r3);

        matrix.setRules(rules);
        priceMatrixRepository.save(matrix);

        System.out.println(">>> SEED COMPLETADO.");
    }

    private void ensureSurfacingProductExists() {
        if (productRepository.findBySku("SERV-TALLADO").isEmpty()) {
            branchRepository.findAll().stream().findFirst().ifPresent(b -> ensureSurfacingProductExists(b.getId()));
        }
    }

    private void ensureSurfacingProductExists(Long branchId) {
        if (productRepository.findBySku("SERV-TALLADO").isEmpty()) {
            createProduct(branchId, "SERV-TALLADO", "Laboratorio", "Servicio de Tallado", ProductType.SERVICE, "250.00", "Servicio");
        }
    }

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
        if (productRepository.findBySku(sku).isPresent()) return;

        Product p = new Product();
        p.setSku(sku);
        p.setBrand(brand);
        p.setModel(model);
        p.setType(type);
        p.setBasePrice(new BigDecimal(price));
        p.setStockQuantity(999);
        p.setBranchId(branchId);
        if (category != null) p.setCategory(category);
        if (type == ProductType.SERVICE) p.setDuration(30); 
        productRepository.save(p);
    }
}