package com.idar.optisaas.service;

import com.idar.optisaas.entity.Product;
import com.idar.optisaas.entity.Sale;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.repository.SaleRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.SaleStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReportService {

    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BranchSettingsService branchSettingsService;

    // Ventas "reales" para reportes: excluimos cotizaciones y canceladas.
    private boolean isRealSale(Sale s) {
        return s.getStatus() != SaleStatus.QUOTATION && s.getStatus() != SaleStatus.CANCELLED;
    }

    // =======================================================================
    // REPORTE DE VENTAS (por periodo)
    // =======================================================================
    @Transactional(readOnly = true)
    public Map<String, Object> salesReport(LocalDate from, LocalDate to) {
        Long branchId = TenantContext.getCurrentBranch();
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();

        List<Sale> sales = saleRepository
                .findByBranchIdAndCreatedAtBetween(branchId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream().filter(this::isRealSale).toList();

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Map<String, BigDecimal> byDay = new TreeMap<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> bySellerAmount = new HashMap<>();
        Map<String, Integer> bySellerCount = new HashMap<>();
        Map<String, int[]> productQty = new HashMap<>();           // nombre -> [cantidad]
        Map<String, BigDecimal> productRevenue = new HashMap<>();   // nombre -> ingreso

        for (Sale s : sales) {
            BigDecimal amount = s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO;
            totalSales = totalSales.add(amount);
            if (s.getDiscountAmount() != null) totalDiscount = totalDiscount.add(s.getDiscountAmount());

            String day = s.getCreatedAt().toLocalDate().toString();
            byDay.merge(day, amount, BigDecimal::add);

            String seller = s.getSeller() != null ? s.getSeller().getFullName() : "Sin vendedor";
            bySellerAmount.merge(seller, amount, BigDecimal::add);
            bySellerCount.merge(seller, 1, Integer::sum);

            if (s.getPayments() != null) {
                s.getPayments().forEach(p -> {
                    String m = p.getMethod() != null ? p.getMethod().name() : "OTRO";
                    byMethod.merge(m, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
                });
            }

            if (s.getItems() != null) {
                s.getItems().forEach(it -> {
                    String name = it.getProductNameSnapshot() != null ? it.getProductNameSnapshot() : "Producto";
                    int q = it.getQuantity() != null ? it.getQuantity() : 0;
                    productQty.computeIfAbsent(name, k -> new int[]{0})[0] += q;
                    productRevenue.merge(name, it.getSubtotal() != null ? it.getSubtotal() : BigDecimal.ZERO, BigDecimal::add);
                });
            }
        }

        int ticketCount = sales.size();
        BigDecimal avgTicket = ticketCount > 0
                ? totalSales.divide(BigDecimal.valueOf(ticketCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Top productos por ingreso (máx 10)
        List<Map<String, Object>> topProducts = productRevenue.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("quantity", productQty.getOrDefault(e.getKey(), new int[]{0})[0]);
                    m.put("revenue", e.getValue());
                    return m;
                }).toList();

        List<Map<String, Object>> bySeller = bySellerAmount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("seller", e.getKey());
                    m.put("total", e.getValue());
                    m.put("tickets", bySellerCount.getOrDefault(e.getKey(), 0));
                    return m;
                }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("totalSales", totalSales);
        result.put("ticketCount", ticketCount);
        result.put("avgTicket", avgTicket);
        result.put("totalDiscount", totalDiscount);
        result.put("byDay", byDay);
        result.put("byPaymentMethod", byMethod);
        result.put("topProducts", topProducts);
        result.put("bySeller", bySeller);
        return result;
    }

    // =======================================================================
    // CUENTAS POR COBRAR (deudores con saldo pendiente)
    // =======================================================================
    @Transactional(readOnly = true)
    public Map<String, Object> receivablesReport() {
        Long branchId = TenantContext.getCurrentBranch();
        List<Sale> sales = saleRepository.findByBranchIdOrderByCreatedAtDesc(branchId);

        BigDecimal totalOwed = BigDecimal.ZERO;
        Map<String, BigDecimal> byClient = new HashMap<>();
        Map<String, LocalDateTime> oldestByClient = new HashMap<>();
        Map<String, Integer> countByClient = new HashMap<>();

        for (Sale s : sales) {
            if (!isRealSale(s)) continue;
            BigDecimal remaining = s.getRemainingBalance();
            if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            String client = s.getClient() != null ? s.getClient().getFullName() : "Cliente General";
            totalOwed = totalOwed.add(remaining);
            byClient.merge(client, remaining, BigDecimal::add);
            countByClient.merge(client, 1, Integer::sum);
            oldestByClient.merge(client, s.getCreatedAt(),
                    (a, b) -> a.isBefore(b) ? a : b);
        }

        List<Map<String, Object>> debtors = byClient.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("client", e.getKey());
                    m.put("owed", e.getValue());
                    m.put("sales", countByClient.getOrDefault(e.getKey(), 0));
                    LocalDateTime oldest = oldestByClient.get(e.getKey());
                    m.put("oldestDate", oldest != null ? oldest.toLocalDate().toString() : null);
                    m.put("daysOld", oldest != null ? java.time.temporal.ChronoUnit.DAYS.between(oldest.toLocalDate(), LocalDate.now()) : 0);
                    return m;
                }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOwed", totalOwed);
        result.put("debtorCount", debtors.size());
        result.put("debtors", debtors);
        return result;
    }

    // =======================================================================
    // VALUACIÓN DE INVENTARIO
    // =======================================================================
    @Transactional(readOnly = true)
    public Map<String, Object> inventoryValuation() {
        Long branchId = TenantContext.getCurrentBranch();
        List<Product> products = productRepository.findByBranchId(branchId);
        int lowStockThreshold = branchSettingsService.getLowStockThreshold(branchId);

        BigDecimal valueAtPrice = BigDecimal.ZERO;
        BigDecimal valueAtCost = BigDecimal.ZERO;
        int totalUnits = 0;

        Map<String, BigDecimal> categoryValue = new HashMap<>();
        Map<String, Integer> categoryUnits = new HashMap<>();
        List<Map<String, Object>> lowStock = new ArrayList<>();

        for (Product p : products) {
            int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
            BigDecimal price = p.getBasePrice() != null ? p.getBasePrice() : BigDecimal.ZERO;
            BigDecimal cost = p.getCost() != null ? p.getCost() : BigDecimal.ZERO;
            BigDecimal stockBd = BigDecimal.valueOf(stock);

            valueAtPrice = valueAtPrice.add(price.multiply(stockBd));
            valueAtCost = valueAtCost.add(cost.multiply(stockBd));
            totalUnits += stock;

            String cat = p.getCategory() != null && !p.getCategory().isBlank() ? p.getCategory() : "Sin categoría";
            categoryValue.merge(cat, price.multiply(stockBd), BigDecimal::add);
            categoryUnits.merge(cat, stock, Integer::sum);

            if (stock <= lowStockThreshold) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getBrand() + " " + p.getModel());
                m.put("sku", p.getSku());
                m.put("stock", stock);
                lowStock.add(m);
            }
        }

        BigDecimal potentialProfit = valueAtPrice.subtract(valueAtCost);
        BigDecimal marginPct = valueAtPrice.compareTo(BigDecimal.ZERO) > 0
                ? potentialProfit.multiply(BigDecimal.valueOf(100)).divide(valueAtPrice, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Map<String, Object>> byCategory = categoryValue.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("value", e.getValue());
                    m.put("units", categoryUnits.getOrDefault(e.getKey(), 0));
                    return m;
                }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productCount", products.size());
        result.put("totalUnits", totalUnits);
        result.put("valueAtPrice", valueAtPrice);
        result.put("valueAtCost", valueAtCost);
        result.put("potentialProfit", potentialProfit);
        result.put("marginPct", marginPct);
        result.put("byCategory", byCategory);
        result.put("lowStock", lowStock);
        return result;
    }
}
