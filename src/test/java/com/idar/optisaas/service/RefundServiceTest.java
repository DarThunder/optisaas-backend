package com.idar.optisaas.service;

import com.idar.optisaas.dto.RefundItemRequest;
import com.idar.optisaas.dto.RefundRequest;
import com.idar.optisaas.dto.RefundResponse;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.ProductType;
import com.idar.optisaas.util.SaleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefundServiceTest {

    private SaleRepository saleRepository;
    private RefundRepository refundRepository;
    private RefundItemRepository refundItemRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private RefundService refundService;

    private static final Long BRANCH_ID = 10L;
    private static final Long OTHER_BRANCH_ID = 20L;

    @BeforeEach
    void setUp() throws Exception {
        saleRepository = mock(SaleRepository.class);
        refundRepository = mock(RefundRepository.class);
        refundItemRepository = mock(RefundItemRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);

        refundService = new RefundService();
        setField("saleRepository", saleRepository);
        setField("refundRepository", refundRepository);
        setField("refundItemRepository", refundItemRepository);
        setField("productRepository", productRepository);
        setField("userRepository", userRepository);
        setField("auditService", auditService);

        TenantContext.setCurrentBranch(BRANCH_ID);

        User processor = new User();
        processor.setId(1L);
        processor.setFullName("Gerente");
        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(processor));

        when(refundItemRepository.findBySaleId(any())).thenReturn(Collections.emptyList());
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        var field = RefundService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(refundService, value);
    }

    // ------------------------- Andamiaje -------------------------

    private Product product(Long id, ProductType type, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setType(type);
        product.setStockQuantity(stock);
        product.setModel("Modelo " + id);
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(product));
        return product;
    }

    private SaleItem saleItem(Long id, Product product, int quantity, String unitPrice) {
        SaleItem item = new SaleItem();
        item.setId(id);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setSubtotal(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        item.setProductNameSnapshot("Pieza " + id);
        return item;
    }

    private Sale sale(Long branchId, String total, String paid, String discount, List<SaleItem> items) {
        Branch branch = new Branch();
        branch.setId(branchId);

        Sale sale = new Sale();
        sale.setId(500L);
        sale.setBranch(branch);
        sale.setBranchId(branchId);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setTotalAmount(new BigDecimal(total));
        sale.setPaidAmount(new BigDecimal(paid));
        sale.setDiscountAmount(new BigDecimal(discount));
        sale.setItems(new ArrayList<>(items));
        items.forEach(item -> item.setSale(sale));

        when(saleRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(sale));
        return sale;
    }

    private RefundRequest request(Long saleItemId, int quantity, boolean restock, String amount) {
        RefundItemRequest item = new RefundItemRequest();
        item.setSaleItemId(saleItemId);
        item.setQuantity(quantity);
        item.setRestock(restock);

        RefundRequest request = new RefundRequest();
        request.setItems(List.of(item));
        request.setReason("Cliente insatisfecho");
        request.setMethod("CASH");
        if (amount != null) request.setAmount(new BigDecimal(amount));
        return request;
    }

    // ------------------------- Pruebas -------------------------

    @Test
    void fullRefundOfPaidSaleReturnsAllMoneyAndRestocks() {
        Product frame = product(1L, ProductType.FRAME, 3);
        SaleItem item = saleItem(100L, frame, 1, "500.00");
        Sale sale = sale(BRANCH_ID, "500.00", "500.00", "0.00", List.of(item));

        RefundResponse response = refundService.createRefund(500L, request(100L, 1, true, null), "gerente");

        assertEquals(0, new BigDecimal("500.00").compareTo(response.getAmount()));
        assertEquals("RETURNED", response.getSaleStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getRemainingBalance()));
        assertEquals(4, frame.getStockQuantity(), "El armazón debe volver al anaquel");
        assertTrue(response.getItems().get(0).isRestocked());
    }

    @Test
    void partialRefundProratesDiscountAndMarksSalePartiallyReturned() {
        // Venta de $1000 bruto con $100 de descuento -> total $900, todo pagado.
        // Se devuelve 1 de las 2 piezas de $200: su parte del descuento es 100 * 400/1000 = 40,
        // así que el valor neto de las dos piezas es 360 y el de una sola, 180.
        Product frame = product(1L, ProductType.FRAME, 0);
        Product accessory = product(2L, ProductType.ACCESSORY, 5);
        SaleItem expensive = saleItem(100L, frame, 1, "600.00");
        SaleItem cheap = saleItem(200L, accessory, 2, "200.00");
        Sale sale = sale(BRANCH_ID, "900.00", "900.00", "100.00", List.of(expensive, cheap));

        RefundResponse response = refundService.createRefund(500L, request(200L, 1, true, null), "gerente");

        assertEquals(0, new BigDecimal("180.00").compareTo(response.getReturnedValue()));
        assertEquals(0, new BigDecimal("180.00").compareTo(response.getAmount()));
        assertEquals("PARTIALLY_RETURNED", response.getSaleStatus());
        assertEquals(6, accessory.getStockQuantity());
        // Se queda con mercancía por $720 y pagó $900: el saldo queda en cero, no en negativo.
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getRemainingBalance()));
    }

    @Test
    void refundOnPartiallyPaidSaleOnlyReducesDebt() {
        // Anticipo de $200 sobre una venta de $1000; devuelve mercancía por $300.
        // No hay dinero que regresar: el cliente aún debe más de lo que pagó.
        Product frame = product(1L, ProductType.FRAME, 0);
        SaleItem kept = saleItem(100L, frame, 1, "700.00");
        SaleItem returned = saleItem(200L, product(2L, ProductType.ACCESSORY, 1), 1, "300.00");
        Sale sale = sale(BRANCH_ID, "1000.00", "200.00", "0.00", List.of(kept, returned));

        RefundResponse response = refundService.createRefund(500L, request(200L, 1, true, null), "gerente");

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getAmount()), "No debe salir dinero de la caja");
        assertNull(response.getMethod(), "Sin salida de dinero no hay medio de reembolso");
        assertEquals(0, new BigDecimal("500.00").compareTo(sale.getRemainingBalance()));
    }

    @Test
    void rejectsRefundAboveWhatTheClientPaid() {
        Product frame = product(1L, ProductType.FRAME, 0);
        SaleItem kept = saleItem(100L, frame, 1, "700.00");
        SaleItem returned = saleItem(200L, product(2L, ProductType.ACCESSORY, 1), 1, "300.00");
        sale(BRANCH_ID, "1000.00", "200.00", "0.00", List.of(kept, returned));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> refundService.createRefund(500L, request(200L, 1, true, "300.00"), "gerente"));
        assertTrue(ex.getMessage().startsWith("El reembolso máximo para esta devolución es $0"), ex.getMessage());
    }

    @Test
    void rejectsReturningMorePiecesThanRemain() {
        Product accessory = product(2L, ProductType.ACCESSORY, 0);
        SaleItem item = saleItem(200L, accessory, 2, "100.00");
        Sale sale = sale(BRANCH_ID, "200.00", "200.00", "0.00", List.of(item));

        // Ya se había devuelto 1 de las 2 piezas.
        RefundItem previous = new RefundItem();
        previous.setSaleItem(item);
        previous.setQuantity(1);
        when(refundItemRepository.findBySaleId(sale.getId())).thenReturn(List.of(previous));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> refundService.createRefund(500L, request(200L, 2, true, null), "gerente"));
        assertTrue(ex.getMessage().contains("solo quedan 1 pieza(s) por devolver"), ex.getMessage());
    }

    @Test
    void doesNotRestockManufacturedLenses() {
        // Un lente se fabrica bajo receta: aunque se pida reingresarlo, no vuelve al anaquel.
        Product lens = product(3L, ProductType.LENS, 0);
        SaleItem item = saleItem(300L, lens, 1, "400.00");
        sale(BRANCH_ID, "400.00", "400.00", "0.00", List.of(item));

        RefundResponse response = refundService.createRefund(500L, request(300L, 1, true, null), "gerente");

        assertFalse(response.getItems().get(0).isRestocked());
        assertEquals(0, lens.getStockQuantity());
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsSaleFromAnotherBranch() {
        Product frame = product(1L, ProductType.FRAME, 1);
        SaleItem item = saleItem(100L, frame, 1, "500.00");
        sale(OTHER_BRANCH_ID, "500.00", "500.00", "0.00", List.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> refundService.createRefund(500L, request(100L, 1, true, null), "gerente"));
        assertEquals("No tienes permiso para devolver ventas de otra sucursal", ex.getMessage());
    }

    @Test
    void rejectsRefundOfQuotationAndOfAlreadyReturnedSale() {
        Product frame = product(1L, ProductType.FRAME, 1);
        SaleItem item = saleItem(100L, frame, 1, "500.00");
        Sale sale = sale(BRANCH_ID, "500.00", "0.00", "0.00", List.of(item));

        sale.setStatus(SaleStatus.QUOTATION);
        assertThrows(RuntimeException.class,
                () -> refundService.createRefund(500L, request(100L, 1, true, null), "gerente"));

        sale.setStatus(SaleStatus.RETURNED);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> refundService.createRefund(500L, request(100L, 1, true, null), "gerente"));
        assertEquals("Esta venta ya se devolvió por completo", ex.getMessage());
    }

    @Test
    void writesAuditTrail() {
        Product frame = product(1L, ProductType.FRAME, 0);
        SaleItem item = saleItem(100L, frame, 1, "500.00");
        sale(BRANCH_ID, "500.00", "500.00", "0.00", List.of(item));

        refundService.createRefund(500L, request(100L, 1, true, null), "gerente");

        verify(auditService).log(eq(com.idar.optisaas.util.AuditAction.SALE_REFUNDED), eq("Sale"), eq(500L), anyString());
    }
}
