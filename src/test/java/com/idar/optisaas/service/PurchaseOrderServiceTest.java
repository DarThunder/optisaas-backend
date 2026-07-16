package com.idar.optisaas.service;

import com.idar.optisaas.dto.ReceiveItemRequest;
import com.idar.optisaas.dto.ReceiveRequest;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.ProductType;
import com.idar.optisaas.util.PurchaseOrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PurchaseOrderServiceTest {

    private PurchaseOrderRepository purchaseOrderRepository;
    private SupplierRepository supplierRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private PurchaseOrderService service;

    private static final Long BRANCH_ID = 10L;
    private static final Long OTHER_BRANCH_ID = 20L;

    @BeforeEach
    void setUp() throws Exception {
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);

        service = new PurchaseOrderService();
        setField("purchaseOrderRepository", purchaseOrderRepository);
        setField("supplierRepository", supplierRepository);
        setField("productRepository", productRepository);
        setField("userRepository", userRepository);
        setField("auditService", auditService);

        TenantContext.setCurrentBranch(BRANCH_ID);

        User user = new User();
        user.setId(1L);
        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(user));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        var field = PurchaseOrderService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    // ------------------------- Andamiaje -------------------------

    private Product product(Long id, int stock, String cost) {
        Product product = new Product();
        product.setId(id);
        product.setBranchId(BRANCH_ID);
        product.setType(ProductType.FRAME);
        product.setModel("Armazón " + id);
        product.setStockQuantity(stock);
        product.setCost(new BigDecimal(cost));
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(product));
        return product;
    }

    private PurchaseOrder order(Long branchId, PurchaseOrderStatus status, List<PurchaseOrderItem> items) {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("Distribuidora Óptica");

        PurchaseOrder order = new PurchaseOrder();
        order.setId(300L);
        order.setBranchId(branchId);
        order.setSupplier(supplier);
        order.setStatus(status);
        order.setItems(new ArrayList<>(items));
        items.forEach(item -> item.setPurchaseOrder(order));

        when(purchaseOrderRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(order));
        return order;
    }

    private PurchaseOrderItem item(Long id, Product product, int ordered, int alreadyReceived, String unitCost) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setProduct(product);
        item.setQuantityOrdered(ordered);
        item.setQuantityReceived(alreadyReceived);
        item.setUnitCost(new BigDecimal(unitCost));
        return item;
    }

    private ReceiveRequest receiveRequest(Long itemId, int quantity, String unitCostOverride) {
        ReceiveItemRequest line = new ReceiveItemRequest();
        line.setPurchaseOrderItemId(itemId);
        line.setQuantity(quantity);
        if (unitCostOverride != null) line.setUnitCost(new BigDecimal(unitCostOverride));

        ReceiveRequest request = new ReceiveRequest();
        request.setItems(List.of(line));
        return request;
    }

    // ------------------------- Pruebas -------------------------

    @Test
    void receivingAddsStockAndAveragesTheCost() {
        // 10 piezas a $100 + 10 piezas a $150 -> promedio ponderado $125.
        Product frame = product(1L, 10, "100.00");
        PurchaseOrder order = order(BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 10, 0, "150.00")));

        service.receive(300L, receiveRequest(50L, 10, null), "gerente");

        assertEquals(20, frame.getStockQuantity());
        assertEquals(0, new BigDecimal("125.00").compareTo(frame.getCost()));
        assertEquals(PurchaseOrderStatus.RECEIVED, order.getStatus());
        assertNotNull(order.getReceivedAt());
    }

    @Test
    void partialReceiptLeavesTheOrderOpen() {
        Product frame = product(1L, 0, "0.00");
        PurchaseOrder order = order(BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 10, 0, "100.00")));

        service.receive(300L, receiveRequest(50L, 4, null), "gerente");

        assertEquals(4, frame.getStockQuantity());
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, order.getStatus());
        assertNull(order.getReceivedAt(), "La orden no está completa: no debe tener fecha de recepción");
        assertEquals(6, order.getItems().get(0).getPendingQuantity());
    }

    @Test
    void productWithoutPreviousCostAdoptsTheCostOfThePurchase() {
        // Producto viejo con stock pero costo 0 (nació antes de que se capturaran costos):
        // no hay nada que promediar, el costo real es el de esta compra.
        Product frame = product(1L, 5, "0.00");
        order(BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 5, 0, "200.00")));

        service.receive(300L, receiveRequest(50L, 5, null), "gerente");

        assertEquals(0, new BigDecimal("200.00").compareTo(frame.getCost()));
        assertEquals(10, frame.getStockQuantity());
    }

    @Test
    void invoiceCostOverridesTheAgreedCost() {
        // El proveedor facturó más caro de lo pactado: manda la factura, tanto para el
        // promedio como para lo que se le debe.
        Product frame = product(1L, 0, "0.00");
        PurchaseOrder order = order(BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 2, 0, "100.00")));

        service.receive(300L, receiveRequest(50L, 2, "130.00"), "gerente");

        assertEquals(0, new BigDecimal("130.00").compareTo(frame.getCost()));
        assertEquals(0, new BigDecimal("260.00").compareTo(order.getReceivedTotal()));
    }

    @Test
    void partialReceiptsAtDifferentCostsDoNotRepriceWhatAlreadyArrived() {
        // Regresión: llegan 4 a $150 y después 6 a $180. Lo que se le debe al proveedor es
        // 4x150 + 6x180 = 1680. Calcularlo como (piezas recibidas × último costo) daría 1800,
        // repreciando hacia atrás las 4 primeras.
        Product frame = product(1L, 0, "0.00");
        PurchaseOrder order = order(BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 10, 0, "150.00")));

        service.receive(300L, receiveRequest(50L, 4, null), "gerente");
        service.receive(300L, receiveRequest(50L, 6, "180.00"), "gerente");

        assertEquals(0, new BigDecimal("1680.00").compareTo(order.getReceivedTotal()));
        assertEquals(PurchaseOrderStatus.RECEIVED, order.getStatus());
        assertEquals(10, frame.getStockQuantity());
    }

    @Test
    void rejectsReceivingMoreThanOrdered() {
        Product frame = product(1L, 0, "0.00");
        order(BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 10, 8, "100.00")));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.receive(300L, receiveRequest(50L, 3, null), "gerente"));
        assertTrue(ex.getMessage().contains("solo faltan 2 pieza(s) por recibir"), ex.getMessage());
    }

    @Test
    void rejectsReceivingADraftOrACancelledOrder() {
        Product frame = product(1L, 0, "0.00");
        PurchaseOrder order = order(BRANCH_ID, PurchaseOrderStatus.DRAFT, List.of(item(50L, frame, 1, 0, "100.00")));

        RuntimeException draftEx = assertThrows(RuntimeException.class,
                () -> service.receive(300L, receiveRequest(50L, 1, null), "gerente"));
        assertEquals("Confirma la orden antes de recibir mercancía", draftEx.getMessage());

        order.setStatus(PurchaseOrderStatus.CANCELLED);
        assertThrows(RuntimeException.class, () -> service.receive(300L, receiveRequest(50L, 1, null), "gerente"));

        assertEquals(0, frame.getStockQuantity(), "Nada de esto debió tocar el inventario");
    }

    @Test
    void rejectsReceivingAnOrderFromAnotherBranch() {
        Product frame = product(1L, 0, "0.00");
        order(OTHER_BRANCH_ID, PurchaseOrderStatus.ORDERED, List.of(item(50L, frame, 1, 0, "100.00")));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.receive(300L, receiveRequest(50L, 1, null), "gerente"));
        assertEquals("No tienes permiso para recibir órdenes de otra sucursal", ex.getMessage());
    }

    @Test
    void cancellingKeepsTheStockAlreadyReceived() {
        Product frame = product(1L, 4, "100.00");
        PurchaseOrder order = order(BRANCH_ID, PurchaseOrderStatus.PARTIALLY_RECEIVED, List.of(item(50L, frame, 10, 4, "100.00")));
        when(purchaseOrderRepository.findByIdAndBranchId(300L, BRANCH_ID)).thenReturn(Optional.of(order));

        service.cancel(300L, "El proveedor ya no tiene el modelo");

        assertEquals(PurchaseOrderStatus.CANCELLED, order.getStatus());
        assertEquals(4, frame.getStockQuantity(), "Lo que ya llegó se queda: cancelar no retira mercancía");
    }
}
