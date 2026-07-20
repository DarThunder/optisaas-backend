package com.idar.optisaas.mail;

import com.idar.optisaas.entity.BranchSettings;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.BranchSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Manda al empleado su código de activación por correo, cuando tiene uno.
 *
 * Si no tiene correo no pasa nada: el administrador sigue viendo el código en pantalla para
 * dictárselo, como siempre. Eso importa porque hoy la mayoría de los empleados no tiene correo
 * capturado; el correo mejora el flujo donde se puede, sin romperlo donde no.
 */
@Component
public class EmployeeActivationMailer {

    private static final Logger log = LoggerFactory.getLogger(EmployeeActivationMailer.class);

    @Autowired private MailSender mailSender;
    @Autowired private BranchSettingsRepository branchSettingsRepository;

    /**
     * Envía el código si el empleado tiene correo.
     *
     * @return el correo al que se envió, o null si no había a dónde mandarlo
     */
    public String sendActivationCode(User employee, Long branchId, boolean isReset) {
        String to = employee.getEmail();
        if (to == null || to.isBlank()) {
            return null;
        }

        Identity identity = resolveIdentity(branchId);
        EmailMessage message = MailTemplates.employeeActivation(
                to,
                employee.getFullName(),
                employee.getUsername(),
                employee.getActivationCode(),
                identity.businessName(),
                identity.replyTo(),
                isReset);

        sendAfterCommit(message);
        return to;
    }

    /**
     * El correo sale cuando la transacción ya se confirmó.
     *
     * Si se enviara dentro de la transacción y esta fallara después, el empleado recibiría un
     * código de una cuenta que no existe (al crear) o que no llegó a cambiar (al resetear).
     * Fuera de una transacción —en pruebas, por ejemplo— se envía directo.
     */
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

    /**
     * Identidad de la sucursal que invita. Se prefiere lo que el dueño configuró en los ajustes
     * del negocio; si no hay nada, se cae a un genérico antes que mandar un correo sin nombre.
     */
    private Identity resolveIdentity(Long branchId) {
        if (branchId == null) {
            return new Identity(null, null);
        }
        Optional<BranchSettings> settings = branchSettingsRepository.findByBranchId(branchId);
        return settings
                .map(s -> new Identity(blankToNull(s.getBusinessName()), blankToNull(s.getEmail())))
                .orElseGet(() -> {
                    log.debug("La sucursal {} no tiene ajustes de negocio; se usa identidad genérica", branchId);
                    return new Identity(null, null);
                });
    }

    /** Sucursal del empleado, para tomar de ahí la identidad del negocio. */
    public Long primaryBranchId(User employee) {
        if (employee.getBranchRoles() == null || employee.getBranchRoles().isEmpty()) {
            return null;
        }
        return employee.getBranchRoles().stream()
                .map(UserBranchRole::getBranch)
                .filter(b -> b != null && b.getId() != null)
                .map(b -> b.getId())
                .findFirst()
                .orElse(null);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private record Identity(String businessName, String replyTo) {}
}
