package com.idar.optisaas.mail;

import com.idar.optisaas.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El correo del código de activación debe salir DESPUÉS de que la transacción se confirma.
 *
 * Existe porque el test unitario del mailer corre sin transacción y por lo tanto ejercita el
 * camino de envío inmediato, no el que se usa en producción: si el registro de la sincronización
 * dejara de funcionar, nadie recibiría su código y ninguna prueba lo notaría.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmployeeActivationMailerTransactionTest {

    @Autowired private EmployeeActivationMailer mailer;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private MailSender mailSender;

    private User employee() {
        User user = new User();
        user.setId(1L);
        user.setEmail("empleado@correo.com");
        user.setUsername("empleado.prueba");
        user.setFullName("Empleado de Prueba");
        user.setActivationCode("123456");
        return user;
    }

    @Test
    void sendsOnlyAfterTheTransactionCommits() {
        transactionTemplate.executeWithoutResult(status -> {
            mailer.sendActivationCode(employee(), null, false);
            // Dentro de la transacción todavía no debe haber salido nada: si la operación
            // fallara aquí, el empleado habría recibido un código de una cuenta inexistente.
            verify(mailSender, never()).send(any());
        });

        // Ya confirmada, el correo sale.
        verify(mailSender, times(1)).send(any(EmailMessage.class));
    }

    @Test
    void doesNotSendWhenTheTransactionRollsBack() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                mailer.sendActivationCode(employee(), null, false);
                throw new IllegalStateException("falla simulada después de programar el correo");
            });
        } catch (IllegalStateException expected) {
            // La transacción revienta a propósito.
        }

        verify(mailSender, never()).send(any());
    }

    @Test
    void sendsImmediatelyWhenThereIsNoTransaction() {
        String sentTo = mailer.sendActivationCode(employee(), null, false);

        assertEquals("empleado@correo.com", sentTo);
        verify(mailSender, times(1)).send(any(EmailMessage.class));
    }
}
