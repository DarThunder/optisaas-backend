package com.idar.optisaas.service;

import com.idar.optisaas.entity.Product;
import com.idar.optisaas.entity.Sale;
import com.idar.optisaas.entity.SalesGoal;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.repository.SaleRepository;
import com.idar.optisaas.repository.UserBranchRoleRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.Role;
import com.idar.optisaas.util.SaleStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reportes consolidados para el DUEÑO: agrega finanzas de TODAS sus sucursales.
 * Seguridad: solo considera las sucursales donde el usuario autenticado es OWNER,
 * por lo que jamás incluye datos de otras cuentas/dueños de la plataforma.
 */
@Service
public class OwnerReportService {

    @Autowired private UserRepository userRepository;
    @Autowired private UserBranchRoleRepository roleRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private com.idar.optisaas.repository.SalesGoalRepository salesGoalRepository;

    private boolean isRealSale(Sale s) {
        return s.getStatus() != SaleStatus.QUOTATION && s.getStatus() != SaleStatus.CANCELLED;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> globalSummary(String ownerUsername, LocalDate from, LocalDate to) {
        User owner = userRepository.findByEmailOrUsername(ownerUsername, ownerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();

        // SOLO sucursales donde este usuario es DUEÑO.
        List<UserBranchRole> ownedRoles = roleRepository.findByUser_IdAndRole(owner.getId(), Role.OWNER);

        BigDecimal totalSales = BigDecimal.ZERO;
        int totalTickets = 0;
        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal totalInvValue = BigDecimal.ZERO;
        BigDecimal totalInvCost = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO; // costo de lo vendido en el periodo

        List<Map<String, Object>> branches = new ArrayList<>();

        for (UserBranchRole r : ownedRoles) {
            if (r.getBranch() == null) continue;
            Long bId = r.getBranch().getId();
            String bName = r.getBranch().getName();

            // Ventas y costo de lo vendido (COGS) del periodo
            BigDecimal bSales = BigDecimal.ZERO;
            BigDecimal bCogs = BigDecimal.ZERO;
            int bTickets = 0;
            for (Sale s : saleRepository.findByBranchIdAndCreatedAtBetween(bId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
                if (!isRealSale(s)) continue;
                bSales = bSales.add(s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO);
                bTickets++;
                if (s.getItems() != null) {
                    for (var it : s.getItems()) {
                        if (it.getProduct() != null && it.getProduct().getCost() != null) {
                            int q = it.getQuantity() != null ? it.getQuantity() : 0;
                            bCogs = bCogs.add(it.getProduct().getCost().multiply(BigDecimal.valueOf(q)));
                        }
                    }
                }
            }
            BigDecimal bProfit = bSales.subtract(bCogs);

            // Cuentas por cobrar (saldo vigente, no acotado al periodo)
            BigDecimal bReceivable = BigDecimal.ZERO;
            for (Sale s : saleRepository.findByBranchIdOrderByCreatedAtDesc(bId)) {
                if (!isRealSale(s)) continue;
                BigDecimal rem = s.getRemainingBalance();
                if (rem != null && rem.compareTo(BigDecimal.ZERO) > 0) bReceivable = bReceivable.add(rem);
            }

            // Valor de inventario
            BigDecimal bInvValue = BigDecimal.ZERO;
            BigDecimal bInvCost = BigDecimal.ZERO;
            for (Product p : productRepository.findByBranchId(bId)) {
                int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
                BigDecimal stockBd = BigDecimal.valueOf(stock);
                if (p.getBasePrice() != null) bInvValue = bInvValue.add(p.getBasePrice().multiply(stockBd));
                if (p.getCost() != null) bInvCost = bInvCost.add(p.getCost().multiply(stockBd));
            }

            BigDecimal bAvgTicket = bTickets > 0
                    ? bSales.divide(BigDecimal.valueOf(bTickets), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> b = new LinkedHashMap<>();
            b.put("branchId", bId);
            b.put("branchName", bName);
            b.put("sales", bSales);
            b.put("tickets", bTickets);
            b.put("avgTicket", bAvgTicket);
            b.put("profit", bProfit);
            b.put("receivable", bReceivable);
            b.put("inventoryValue", bInvValue);
            // salesShare (participación %) se calcula abajo, cuando ya se conoce el total.
            branches.add(b);

            totalSales = totalSales.add(bSales);
            totalTickets += bTickets;
            totalCogs = totalCogs.add(bCogs);
            totalReceivable = totalReceivable.add(bReceivable);
            totalInvValue = totalInvValue.add(bInvValue);
            totalInvCost = totalInvCost.add(bInvCost);
        }

        // Participación % de cada sucursal sobre las ventas totales.
        for (Map<String, Object> b : branches) {
            BigDecimal bSales = (BigDecimal) b.get("sales");
            BigDecimal share = totalSales.compareTo(BigDecimal.ZERO) > 0
                    ? bSales.multiply(BigDecimal.valueOf(100)).divide(totalSales, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            b.put("salesShare", share);
        }

        // Ordenar sucursales por ventas (mayor a menor)
        branches.sort((a, b) -> ((BigDecimal) b.get("sales")).compareTo((BigDecimal) a.get("sales")));

        BigDecimal avgTicket = totalTickets > 0
                ? totalSales.divide(BigDecimal.valueOf(totalTickets), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal grossProfit = totalSales.subtract(totalCogs);
        BigDecimal marginPct = totalSales.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(totalSales, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("branchCount", branches.size());
        result.put("totalSales", totalSales);
        result.put("totalTickets", totalTickets);
        result.put("avgTicket", avgTicket);
        result.put("totalCogs", totalCogs);
        result.put("grossProfit", grossProfit);
        result.put("marginPct", marginPct);
        result.put("totalReceivable", totalReceivable);
        result.put("totalInventoryValue", totalInvValue);
        result.put("totalInventoryCost", totalInvCost);
        result.put("branches", branches);
        return result;
    }

    /**
     * Tendencias consolidadas para el DUEÑO: serie de ventas por día en el periodo
     * y comparativo contra el periodo inmediatamente anterior de igual longitud.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> globalTrends(String ownerUsername, LocalDate from, LocalDate to) {
        User owner = userRepository.findByEmailOrUsername(ownerUsername, ownerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();

        // Periodo anterior de igual longitud, inmediatamente antes de 'start'.
        long lengthDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(lengthDays - 1);

        List<UserBranchRole> ownedRoles = roleRepository.findByUser_IdAndRole(owner.getId(), Role.OWNER);

        // Ventas por día del periodo actual (consolidado multisucursal), inicializado en 0.
        Map<LocalDate, BigDecimal> dailyMap = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dailyMap.put(d, BigDecimal.ZERO);
        }

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal prevTotalSales = BigDecimal.ZERO;

        for (UserBranchRole r : ownedRoles) {
            if (r.getBranch() == null) continue;
            Long bId = r.getBranch().getId();

            // Periodo actual
            for (Sale s : saleRepository.findByBranchIdAndCreatedAtBetween(bId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
                if (!isRealSale(s)) continue;
                BigDecimal amt = s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO;
                totalSales = totalSales.add(amt);
                LocalDate day = s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate() : null;
                if (day != null && dailyMap.containsKey(day)) {
                    dailyMap.put(day, dailyMap.get(day).add(amt));
                }
            }

            // Periodo anterior (solo total para el comparativo)
            for (Sale s : saleRepository.findByBranchIdAndCreatedAtBetween(bId, prevStart.atStartOfDay(), prevEnd.plusDays(1).atStartOfDay())) {
                if (!isRealSale(s)) continue;
                prevTotalSales = prevTotalSales.add(s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO);
            }
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : dailyMap.entrySet()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", e.getKey().toString());
            d.put("sales", e.getValue());
            days.add(d);
        }

        // Crecimiento %: (actual - anterior) / anterior * 100. Si el anterior es 0, no es calculable.
        BigDecimal growthPct = null;
        if (prevTotalSales.compareTo(BigDecimal.ZERO) > 0) {
            growthPct = totalSales.subtract(prevTotalSales)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevTotalSales, 1, RoundingMode.HALF_UP);
        }

        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("from", prevStart.toString());
        previous.put("to", prevEnd.toString());
        previous.put("totalSales", prevTotalSales);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("totalSales", totalSales);
        result.put("days", days);
        result.put("previous", previous);
        result.put("growthPct", growthPct);
        return result;
    }

    /** Umbral de existencias para considerar un producto "próximo a agotarse". */
    private static final int LOW_STOCK_THRESHOLD = 5;

    /**
     * Inventario consolidado para el DUEÑO: productos más vendidos del periodo,
     * productos próximos a agotarse / agotados, y productos sin movimiento.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> globalInventory(String ownerUsername, LocalDate from, LocalDate to) {
        User owner = userRepository.findByEmailOrUsername(ownerUsername, ownerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();

        List<UserBranchRole> ownedRoles = roleRepository.findByUser_IdAndRole(owner.getId(), Role.OWNER);

        // Acumuladores de "más vendidos", consolidados por nombre de producto.
        Map<String, BigDecimal> soldQty = new LinkedHashMap<>();
        Map<String, BigDecimal> soldRevenue = new LinkedHashMap<>();
        // IDs de productos con al menos una venta en el periodo (para "sin movimiento").
        java.util.Set<Long> soldProductIds = new java.util.HashSet<>();

        List<Map<String, Object>> lowStock = new ArrayList<>();
        List<Map<String, Object>> noMovement = new ArrayList<>();
        int outOfStockCount = 0;

        for (UserBranchRole r : ownedRoles) {
            if (r.getBranch() == null) continue;
            Long bId = r.getBranch().getId();
            String bName = r.getBranch().getName();

            // Ventas del periodo: acumular cantidades e ingresos por producto.
            for (Sale s : saleRepository.findByBranchIdAndCreatedAtBetween(bId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
                if (!isRealSale(s) || s.getItems() == null) continue;
                for (var it : s.getItems()) {
                    int q = it.getQuantity() != null ? it.getQuantity() : 0;
                    BigDecimal rev = it.getSubtotal() != null ? it.getSubtotal() : BigDecimal.ZERO;
                    String name = it.getProductNameSnapshot();
                    if ((name == null || name.isBlank()) && it.getProduct() != null) {
                        name = it.getProduct().getBrand() + " " + it.getProduct().getModel();
                    }
                    if (name == null || name.isBlank()) name = "Sin nombre";
                    soldQty.merge(name, BigDecimal.valueOf(q), BigDecimal::add);
                    soldRevenue.merge(name, rev, BigDecimal::add);
                    if (it.getProduct() != null) soldProductIds.add(it.getProduct().getId());
                }
            }

            // Estado de existencias por producto de la sucursal.
            for (Product p : productRepository.findByBranchId(bId)) {
                int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
                String pName = (p.getBrand() + " " + p.getModel()).trim();
                if (stock <= LOW_STOCK_THRESHOLD) {
                    if (stock == 0) outOfStockCount++;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", p.getId());
                    row.put("name", pName);
                    row.put("sku", p.getSku());
                    row.put("branchName", bName);
                    row.put("stock", stock);
                    lowStock.add(row);
                }
            }
        }

        // Sin movimiento: productos con existencia > 0 que no se vendieron en el periodo.
        for (UserBranchRole r : ownedRoles) {
            if (r.getBranch() == null) continue;
            String bName = r.getBranch().getName();
            for (Product p : productRepository.findByBranchId(r.getBranch().getId())) {
                int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
                if (stock > 0 && !soldProductIds.contains(p.getId())) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", p.getId());
                    row.put("name", (p.getBrand() + " " + p.getModel()).trim());
                    row.put("sku", p.getSku());
                    row.put("branchName", bName);
                    row.put("stock", stock);
                    BigDecimal invValue = p.getBasePrice() != null
                            ? p.getBasePrice().multiply(BigDecimal.valueOf(stock)) : BigDecimal.ZERO;
                    row.put("inventoryValue", invValue);
                    noMovement.add(row);
                }
            }
        }

        // Top 10 más vendidos por cantidad.
        List<Map<String, Object>> topProducts = new ArrayList<>();
        soldQty.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", e.getKey());
                    row.put("qtySold", e.getValue());
                    row.put("revenue", soldRevenue.getOrDefault(e.getKey(), BigDecimal.ZERO));
                    topProducts.add(row);
                });

        // Ordenar: agotados/menor existencia primero; sin movimiento por mayor valor inmovilizado.
        lowStock.sort((a, b) -> Integer.compare((int) a.get("stock"), (int) b.get("stock")));
        noMovement.sort((a, b) -> ((BigDecimal) b.get("inventoryValue")).compareTo((BigDecimal) a.get("inventoryValue")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("lowStockThreshold", LOW_STOCK_THRESHOLD);
        result.put("topProducts", topProducts);
        result.put("lowStock", lowStock);
        result.put("noMovement", noMovement);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("lowStock", lowStock.size());
        counts.put("outOfStock", outOfStockCount);
        counts.put("noMovement", noMovement.size());
        result.put("counts", counts);
        return result;
    }

    /** Suma de ventas reales del dueño (todas sus sucursales) en el rango [start, end]. */
    private BigDecimal sumOwnerSales(Long ownerId, LocalDate start, LocalDate end) {
        BigDecimal total = BigDecimal.ZERO;
        for (UserBranchRole r : roleRepository.findByUser_IdAndRole(ownerId, Role.OWNER)) {
            if (r.getBranch() == null) continue;
            for (Sale s : saleRepository.findByBranchIdAndCreatedAtBetween(
                    r.getBranch().getId(), start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
                if (!isRealSale(s)) continue;
                total = total.add(s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO);
            }
        }
        return total;
    }

    /**
     * Meta mensual del dueño con avance real y proyección de cierre de mes.
     * Si no hay año/mes se usa el mes actual.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyGoal(String ownerUsername, Integer year, Integer month) {
        User owner = userRepository.findByEmailOrUsername(ownerUsername, ownerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        LocalDate first = LocalDate.of(y, m, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        // El avance real se mide hasta hoy (si el mes es el actual) o hasta fin de mes (si ya pasó).
        LocalDate actualEnd = now.isBefore(last) ? now : last;

        BigDecimal target = salesGoalRepository.findByOwnerIdAndYearAndMonth(owner.getId(), y, m)
                .map(SalesGoal::getTargetAmount).orElse(null);
        BigDecimal actual = sumOwnerSales(owner.getId(), first, actualEnd);

        int daysInMonth = first.lengthOfMonth();
        int daysElapsed = (int) ChronoUnit.DAYS.between(first, actualEnd) + 1;

        // Proyección de cierre: ritmo diario actual extrapolado a todo el mes.
        BigDecimal projection = daysElapsed > 0
                ? actual.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(daysInMonth))
                : BigDecimal.ZERO;

        BigDecimal progressPct = null;
        BigDecimal projectedPct = null;
        if (target != null && target.compareTo(BigDecimal.ZERO) > 0) {
            progressPct = actual.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
            projectedPct = projection.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", y);
        result.put("month", m);
        result.put("target", target);
        result.put("actual", actual);
        result.put("progressPct", progressPct);
        result.put("projection", projection);
        result.put("projectedPct", projectedPct);
        result.put("daysElapsed", daysElapsed);
        result.put("daysInMonth", daysInMonth);
        return result;
    }

    /** Crea o actualiza la meta mensual global del dueño. */
    @Transactional
    public Map<String, Object> setMonthlyGoal(String ownerUsername, Integer year, Integer month, BigDecimal target) {
        User owner = userRepository.findByEmailOrUsername(ownerUsername, ownerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (target == null || target.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("La meta debe ser un monto válido (mayor o igual a 0).");
        }
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        if (m < 1 || m > 12) throw new RuntimeException("Mes inválido.");

        SalesGoal goal = salesGoalRepository.findByOwnerIdAndYearAndMonth(owner.getId(), y, m)
                .orElseGet(() -> {
                    SalesGoal g = new SalesGoal();
                    g.setOwnerId(owner.getId());
                    g.setYear(y);
                    g.setMonth(m);
                    return g;
                });
        goal.setTargetAmount(target);
        salesGoalRepository.save(goal);

        // Devolvemos el estado recalculado (meta + avance) para refrescar el frontend directo.
        return getMonthlyGoal(ownerUsername, y, m);
    }
}
