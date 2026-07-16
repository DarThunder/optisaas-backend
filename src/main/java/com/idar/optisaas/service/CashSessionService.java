package com.idar.optisaas.service;

import com.idar.optisaas.entity.CashSession;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.CashMovementRepository;
import com.idar.optisaas.repository.CashSessionRepository;
import com.idar.optisaas.repository.PaymentRepository;
import com.idar.optisaas.repository.RefundRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.CashMovementType;
import com.idar.optisaas.util.CashSessionStatus;
import com.idar.optisaas.util.PaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CashSessionService {

    @Autowired private CashSessionRepository sessionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private CashMovementRepository movementRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;

    // ---------------------------------------------------------------
    // ABRIR CAJA
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> openSession(BigDecimal openingFloat, String username) {
        Long branchId = TenantContext.getCurrentBranch();

        sessionRepository.findFirstByBranchIdAndStatusOrderByCreatedAtDesc(branchId, CashSessionStatus.OPEN)
                .ifPresent(s -> { throw new RuntimeException("Ya hay una caja abierta en esta sucursal. Ciérrala antes de abrir otra."); });

        User user = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        CashSession session = new CashSession();
        session.setStatus(CashSessionStatus.OPEN);
        session.setOpenedBy(user);
        session.setOpenedAt(LocalDateTime.now());
        session.setOpeningFloat(openingFloat != null ? openingFloat : BigDecimal.ZERO);

        CashSession saved = sessionRepository.save(session);

        auditService.log(AuditAction.CASH_SESSION_OPENED, "CashSession", saved.getId(),
                "fondo inicial: " + saved.getOpeningFloat());

        return currentSession(); // devuelve la caja recién abierta con su tally
    }

    // ---------------------------------------------------------------
    // ESTADO ACTUAL DE LA CAJA (con arqueo en vivo)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public Map<String, Object> currentSession() {
        Long branchId = TenantContext.getCurrentBranch();
        CashSession session = sessionRepository
                .findFirstByBranchIdAndStatusOrderByCreatedAtDesc(branchId, CashSessionStatus.OPEN)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        if (session == null) {
            result.put("open", false);
            return result;
        }

        Map<String, BigDecimal> tally = computeTally(branchId, session.getOpenedAt(), LocalDateTime.now(), session.getOpeningFloat());

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", session.getId());
        s.put("openedByName", session.getOpenedBy() != null ? session.getOpenedBy().getFullName() : null);
        s.put("openedAt", session.getOpenedAt());
        s.put("openingFloat", session.getOpeningFloat());

        result.put("open", true);
        result.put("session", s);
        result.put("tally", tally);
        return result;
    }

    // ---------------------------------------------------------------
    // CERRAR CAJA (arqueo final -> corte guardado)
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> closeSession(BigDecimal countedCash, String username) {
        Long branchId = TenantContext.getCurrentBranch();
        CashSession session = sessionRepository
                .findFirstByBranchIdAndStatusOrderByCreatedAtDesc(branchId, CashSessionStatus.OPEN)
                .orElseThrow(() -> new RuntimeException("No hay una caja abierta para cerrar"));

        User user = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDateTime closeAt = LocalDateTime.now();
        Map<String, BigDecimal> tally = computeTally(branchId, session.getOpenedAt(), closeAt, session.getOpeningFloat());

        BigDecimal expected = tally.get("expectedCash");
        BigDecimal counted = countedCash != null ? countedCash : BigDecimal.ZERO;

        session.setStatus(CashSessionStatus.CLOSED);
        session.setClosedBy(user);
        session.setClosedAt(closeAt);
        session.setExpectedCash(expected);
        session.setCountedCash(counted);
        session.setDifference(counted.subtract(expected));

        sessionRepository.save(session);

        // El corte es la acción de dinero más delicada: registramos esperado vs contado
        // y la diferencia, que es justo lo que se audita en una disputa.
        auditService.log(AuditAction.CASH_SESSION_CLOSED, "CashSession", session.getId(),
                "esperado: " + expected + "; contado: " + counted
                        + "; diferencia: " + session.getDifference()
                        + "; fondo inicial: " + session.getOpeningFloat());

        Map<String, Object> result = new LinkedHashMap<>(tally);
        result.put("id", session.getId());
        result.put("openedAt", session.getOpenedAt());
        result.put("closedAt", closeAt);
        result.put("openingFloat", session.getOpeningFloat());
        result.put("countedCash", counted);
        result.put("difference", session.getDifference());
        return result;
    }

    // ---------------------------------------------------------------
    // Cálculo del arqueo para una ventana [start, end]
    // ---------------------------------------------------------------
    private Map<String, BigDecimal> computeTally(Long branchId, LocalDateTime start, LocalDateTime end, BigDecimal openingFloat) {
        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal transferSales = BigDecimal.ZERO;

        for (var p : paymentRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end)) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            PaymentMethod method = p.getMethod();
            if (method == PaymentMethod.CASH) cashSales = cashSales.add(amt);
            else if (method == PaymentMethod.CARD || method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD) cardSales = cardSales.add(amt);
            else if (method == PaymentMethod.TRANSFER) transferSales = transferSales.add(amt);
        }

        // Los reembolsos son dinero que salió: se restan del cobrado por su mismo medio.
        BigDecimal cashRefunds = BigDecimal.ZERO;
        BigDecimal cardRefunds = BigDecimal.ZERO;
        BigDecimal transferRefunds = BigDecimal.ZERO;

        for (var r : refundRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end)) {
            BigDecimal amt = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
            PaymentMethod method = r.getMethod();
            if (method == PaymentMethod.CASH) cashRefunds = cashRefunds.add(amt);
            else if (method == PaymentMethod.CARD || method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD) cardRefunds = cardRefunds.add(amt);
            else if (method == PaymentMethod.TRANSFER) transferRefunds = transferRefunds.add(amt);
        }

        BigDecimal cashIncome = BigDecimal.ZERO;
        BigDecimal cashExpense = BigDecimal.ZERO;
        for (var m : movementRepository.findByBranchIdAndCreatedAtBetweenOrderByCreatedAtDesc(branchId, start, end)) {
            BigDecimal amt = m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
            if (m.getType() == CashMovementType.INCOME) cashIncome = cashIncome.add(amt);
            else if (m.getType() == CashMovementType.EXPENSE) cashExpense = cashExpense.add(amt);
        }

        BigDecimal base = openingFloat != null ? openingFloat : BigDecimal.ZERO;
        BigDecimal expectedCash = base.add(cashSales).add(cashIncome)
                .subtract(cashExpense).subtract(cashRefunds);

        Map<String, BigDecimal> tally = new LinkedHashMap<>();
        tally.put("cashSales", cashSales);
        tally.put("cardSales", cardSales);
        tally.put("transferSales", transferSales);
        tally.put("cashRefunds", cashRefunds);
        tally.put("cardRefunds", cardRefunds);
        tally.put("transferRefunds", transferRefunds);
        tally.put("cashIncome", cashIncome);
        tally.put("cashExpense", cashExpense);
        tally.put("expectedCash", expectedCash);
        return tally;
    }
}
