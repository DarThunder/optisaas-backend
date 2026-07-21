package com.idar.optisaas.mail;

import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.BranchSettings;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.BranchSettingsRepository;
import com.idar.optisaas.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EmployeeActivationMailerTest {

    private MailSender mailSender;
    private BranchSettingsRepository settingsRepository;
    private EmployeeActivationMailer mailer;

    private static final Long BRANCH_ID = 10L;

    @BeforeEach
    void setUp() throws Exception {
        mailSender = mock(MailSender.class);
        settingsRepository = mock(BranchSettingsRepository.class);

        mailer = new EmployeeActivationMailer();
        setField("mailSender", mailSender);
        setField("mailTemplates", new MailTemplates("Fóvea", "VLK"));
        setField("branchSettingsRepository", settingsRepository);

        BranchSettings settings = new BranchSettings();
        settings.setBusinessName("Óptica Vista Clara");
        settings.setEmail("hola@vistaclara.mx");
        when(settingsRepository.findByBranchId(BRANCH_ID)).thenReturn(Optional.of(settings));
    }

    private void setField(String name, Object value) throws Exception {
        var field = EmployeeActivationMailer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mailer, value);
    }

    private User employee(String email) {
        User user = new User();
        user.setId(5L);
        user.setEmail(email);
        user.setUsername("ana.vend");
        user.setFullName("Ana Vendedora");
        user.setActivationCode("482913");
        return user;
    }

    private EmailMessage captureSent() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendsTheCodeWhenTheEmployeeHasEmail() {
        String sentTo = mailer.sendActivationCode(employee("ana@correo.com"), BRANCH_ID, false);

        assertEquals("ana@correo.com", sentTo);
        EmailMessage sent = captureSent();
        assertTrue(sent.textBody().contains("482913"), "el código debe ir en el correo");
        assertTrue(sent.textBody().contains("ana.vend"), "el usuario debe ir en el correo");
    }

    // La mayoría de los empleados no tiene correo capturado: ahí no se manda nada y el
    // administrador les dicta el código como siempre.
    @Test
    void doesNothingWhenTheEmployeeHasNoEmail() {
        assertNull(mailer.sendActivationCode(employee(null), BRANCH_ID, false));
        assertNull(mailer.sendActivationCode(employee("   "), BRANCH_ID, false));

        verify(mailSender, never()).send(any());
    }

    // Identidad: el empleado debe reconocer QUIÉN lo dio de alta (puede trabajar en varias
    // ópticas), y las respuestas tienen que llegarle a la óptica, no a la plataforma.
    @Test
    void usesTheBranchIdentityAsSender() {
        mailer.sendActivationCode(employee("ana@correo.com"), BRANCH_ID, false);

        EmailMessage sent = captureSent();
        assertEquals("Óptica Vista Clara vía Fóvea", sent.fromName());
        assertEquals("hola@vistaclara.mx", sent.replyTo());
        assertTrue(sent.subject().contains("Óptica Vista Clara"));
    }

    // Una sucursal sin ajustes configurados no debe impedir el envío ni mandar un correo
    // firmado por nadie.
    @Test
    void fallsBackToAGenericIdentityWhenTheBranchHasNoSettings() {
        when(settingsRepository.findByBranchId(anyLong())).thenReturn(Optional.empty());

        String sentTo = mailer.sendActivationCode(employee("ana@correo.com"), 99L, false);

        assertEquals("ana@correo.com", sentTo);
        EmailMessage sent = captureSent();
        assertEquals("Tu óptica vía Fóvea", sent.fromName());
        assertNull(sent.replyTo());
    }

    @Test
    void resetEmailSaysTheAccessWasRestored() {
        mailer.sendActivationCode(employee("ana@correo.com"), BRANCH_ID, true);

        EmailMessage sent = captureSent();
        assertTrue(sent.subject().contains("restableció"));
        assertTrue(sent.textBody().contains("restableció"));
        assertTrue(sent.textBody().contains("482913"));
    }

    @Test
    void resolvesThePrimaryBranchOfAnEmployee() {
        User user = employee("ana@correo.com");
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        UserBranchRole role = new UserBranchRole();
        role.setUser(user);
        role.setBranch(branch);
        role.setRole(Role.SELLER);
        user.setBranchRoles(Set.of(role));

        assertEquals(BRANCH_ID, mailer.primaryBranchId(user));
    }

    @Test
    void primaryBranchIsNullWhenTheEmployeeHasNoRolesYet() {
        assertNull(mailer.primaryBranchId(employee("ana@correo.com")));
    }
}
