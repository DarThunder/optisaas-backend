package com.idar.optisaas.service;

import com.idar.optisaas.entity.Product;
import com.idar.optisaas.entity.Sale;
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

            Map<String, Object> b = new LinkedHashMap<>();
            b.put("branchId", bId);
            b.put("branchName", bName);
            b.put("sales", bSales);
            b.put("tickets", bTickets);
            b.put("profit", bProfit);
            b.put("receivable", bReceivable);
            b.put("inventoryValue", bInvValue);
            branches.add(b);

            totalSales = totalSales.add(bSales);
            totalTickets += bTickets;
            totalCogs = totalCogs.add(bCogs);
            totalReceivable = totalReceivable.add(bReceivable);
            totalInvValue = totalInvValue.add(bInvValue);
            totalInvCost = totalInvCost.add(bInvCost);
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
}
