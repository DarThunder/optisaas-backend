package com.idar.optisaas.mail;

import com.idar.optisaas.entity.BranchSettings;
import com.idar.optisaas.entity.Sale;
import com.idar.optisaas.entity.SaleItem;
import com.idar.optisaas.repository.BranchSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Manda al cliente su comprobante de compra.
 *
 * A diferencia de los avisos de "tu pedido está listo", este correo NO se dispara solo: lo pide
 * quien atiende. Mandar un correo a un cliente porque compró, sin que lo haya pedido, es correo
 * no solicitado; que alguien lo pida en el mostrador es consentimiento explícito.
 */
@Component
public class SaleReceiptMailer {

    @Autowired private MailSender mailSender;
    @Autowired private MailTemplates mailTemplates;
    @Autowired private BranchSettingsRepository branchSettingsRepository;

    /**
     * Arma y envía el comprobante.
     *
     * @param to correo destino (el del cliente, o el que se capture en el momento)
     */
    public void sendReceipt(Sale sale, String to) {
        ReceiptData data = toReceiptData(sale);
        EmailMessage message = mailTemplates.saleReceipt(to, data);
        sendAfterCommit(message);
    }

    private ReceiptData toReceiptData(Sale sale) {
        List<ReceiptData.Line> lines = sale.getItems().stream()
                .map(this::toLine)
                .toList();

        return new ReceiptData(
                sale.getId(),
                sale.getCreatedAt(),
                sale.getClient() != null ? sale.getClient().getFullName() : null,
                sale.getSeller() != null ? sale.getSeller().getFullName() : null,
                lines,
                sale.getDiscountAmount(),
                sale.getDiscountName(),
                sale.getTotalAmount(),
                sale.getPaidAmount(),
                sale.getRemainingBalance(),
                resolveBusiness(sale.getBranchId()));
    }

    private ReceiptData.Line toLine(SaleItem item) {
        // Se usa el nombre guardado al vender, no el actual del producto: si mañana le cambian
        // el nombre o lo dan de baja, el comprobante debe seguir diciendo lo que se compró.
        String description = item.getProductNameSnapshot() != null
                ? item.getProductNameSnapshot()
                : (item.getProduct() != null ? item.getProduct().getModel() : "Producto");

        return new ReceiptData.Line(
                description,
                item.getQuantity() != null ? item.getQuantity() : 0,
                item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO,
                item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO);
    }

    /** Identidad del negocio; si la sucursal no tiene ajustes, el comprobante sale sin firma. */
    private ReceiptData.Business resolveBusiness(Long branchId) {
        Optional<BranchSettings> settings = branchId == null
                ? Optional.empty()
                : branchSettingsRepository.findByBranchId(branchId);

        return settings
                .map(s -> new ReceiptData.Business(
                        blankToNull(s.getBusinessName()),
                        blankToNull(s.getAddressLine()),
                        blankToNull(s.getPhone()),
                        blankToNull(s.getEmail()),
                        blankToNull(s.getTaxId()),
                        blankToNull(s.getFooterMessage()),
                        blankToNull(s.getLegalNote())))
                .orElseGet(() -> new ReceiptData.Business(null, null, null, null, null, null, null));
    }

    /** Igual que en la activación: el correo sale cuando la transacción ya se confirmó. */
    private void sendAfterCommit(EmailMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailSender.send(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailSender.send(message);
            }
        });
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
