package com.idar.optisaas.service;

import com.idar.optisaas.dto.InventoryAdjustmentRequest;
import com.idar.optisaas.entity.InventoryAdjustment;
import com.idar.optisaas.entity.Product;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.InventoryAdjustmentRepository;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AdjustmentReason;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.ProductType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryAdjustmentServiceTest {

    private InventoryAdjustmentRepository adjustmentRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private InventoryAdjustmentService service;

    private static final Long BRANCH_ID = 10L;

    @BeforeEach
    void setUp() throws Exception {
        adjustmentRepository = mock(InventoryAdjustmentRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);

        service = new InventoryAdjustmentService();
        setField("adjustmentRepository", adjustmentRepository);
        setField("productRepository", productRepository);
        setField("userRepository", userRepository);
        setField("auditService", auditService);

        TenantContext.setCurrentBranch(BRANCH_ID);

        User user = new User();
        user.setId(1L);
        user.setFullName("Gerente");
        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(user));
        when(adjustmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        var field = InventoryAdjustmentService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private Product product(Long branchId, int stock, String cost) {
        Product product = new Product();
        product.setId(1L);
        product.setBranchId(branchId);
        product.setType(ProductType.FRAME);
        product.setModel("Armazón");
        product.setStockQuantity(stock);
        product.setCost(new BigDecimal(cost));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        return product;
    }

    private InventoryAdjustmentRequest request(Integer newQuantity, Integer delta, String reason) {
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest();
        request.setProductId(1L);
        request.setNewQuantity(newQuantity);
        request.setDelta(delta);
        request.setReason(reason);
        request.setNote("Se cayó del mostrador");
        return request;
    }

    @Test
    void shrinkageByDeltaLowersStockAndValuesTheLoss() {
        Product frame = product(BRANCH_ID, 10, "250.00");

        InventoryAdjustment result = service.adjust(request(null, -2, "SHRINKAGE"), "gerente");

        assertEquals(8, frame.getStockQuantity());
        assertEquals(10, result.getPreviousQuantity());
        assertEquals(8, result.getNewQuantity());
        assertEquals(-2, result.getDelta());
        assertEquals(AdjustmentReason.SHRINKAGE, result.getReason());
        // El costo se congela: valuar la merma no debe depender de lo que cueste el producto mañana.
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getUnitCostSnapshot()));
    }

    @Test
    void physicalCountComputesTheDifference() {
        // "Conté 7 y el sistema decía 9": el ajuste es de -2 sin que nadie lo calcule a mano.
        Product frame = product(BRANCH_ID, 9, "100.00");

        InventoryAdjustment result = service.adjust(request(7, null, "COUNT_CORRECTION"), "gerente");

        assertEquals(7, frame.getStockQuantity());
        assertEquals(-2, result.getDelta());
    }

    @Test
    void rejectsAdjustmentThatWouldLeaveNegativeStock() {
        Product frame = product(BRANCH_ID, 3, "100.00");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adjust(request(null, -5, "THEFT"), "gerente"));
        assertTrue(ex.getMessage().contains("no puedes descontar más piezas de las que hay (3)"), ex.getMessage());
        assertEquals(3, frame.getStockQuantity(), "El stock no debió moverse");
    }

    @Test
    void requiresExactlyOneOfCountOrDelta() {
        product(BRANCH_ID, 5, "100.00");

        RuntimeException both = assertThrows(RuntimeException.class,
                () -> service.adjust(request(3, -2, "SHRINKAGE"), "gerente"));
        assertTrue(both.getMessage().contains("pero no ambos"));

        assertThrows(RuntimeException.class, () -> service.adjust(request(null, null, "SHRINKAGE"), "gerente"));
    }

    @Test
    void rejectsZeroDeltaAndUnknownReason() {
        product(BRANCH_ID, 5, "100.00");

        assertThrows(RuntimeException.class, () -> service.adjust(request(null, 0, "SHRINKAGE"), "gerente"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adjust(request(null, -1, "PORQUE_SI"), "gerente"));
        assertTrue(ex.getMessage().startsWith("Motivo de ajuste inválido"), ex.getMessage());
    }

    @Test
    void rejectsProductFromAnotherBranch() {
        Product foreign = product(99L, 10, "100.00");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adjust(request(null, -1, "SHRINKAGE"), "gerente"));
        assertEquals("El producto no pertenece a esta sucursal", ex.getMessage());
        assertEquals(10, foreign.getStockQuantity());
    }

    @Test
    void writesAuditTrail() {
        product(BRANCH_ID, 10, "250.00");

        service.adjust(request(null, -2, "SHRINKAGE"), "gerente");

        verify(auditService).log(eq(AuditAction.INVENTORY_ADJUSTED), eq("Product"), eq(1L), anyString());
    }
}
