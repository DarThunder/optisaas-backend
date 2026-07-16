package com.idar.optisaas.service;

import com.idar.optisaas.entity.AuditLog;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.AuditLogRepository;
import com.idar.optisaas.repository.UserBranchRoleRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {

    private AuditLogRepository auditLogRepository;
    private UserRepository userRepository;
    private UserBranchRoleRepository roleRepository;
    private AuditQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository = mock(AuditLogRepository.class);
        userRepository = mock(UserRepository.class);
        roleRepository = mock(UserBranchRoleRepository.class);

        service = new AuditQueryService();
        setField("auditLogRepository", auditLogRepository);
        setField("userRepository", userRepository);
        setField("roleRepository", roleRepository);

        User user = new User();
        user.setId(7L);
        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(user));
    }

    private void setField(String name, Object value) throws Exception {
        var field = AuditQueryService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    void returnsEmptyWhenUserOwnsNoBranches() {
        // Aislamiento: quien no es OWNER de ninguna sucursal no ve bitácora alguna,
        // y ni siquiera se consulta el repositorio.
        when(roleRepository.findByUser_IdAndRole(7L, Role.OWNER)).thenReturn(Collections.emptyList());

        Page<AuditLog> result = service.search("alguien", null, null, null, 0, 50);

        assertTrue(result.isEmpty());
        verify(auditLogRepository, never()).findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.search("fantasma", null, null, null, 0, 50));
    }

    @Test
    void clampsPageSizeToUpperBound() throws Exception {
        // Un tamaño desmedido no debe permitir traer toda la bitácora de golpe.
        var method = AuditQueryService.class.getDeclaredMethod("normalizeSize", int.class);
        method.setAccessible(true);

        assertEquals(200, method.invoke(service, 100_000));
        assertEquals(50, method.invoke(service, 0));
        assertEquals(25, method.invoke(service, 25));
    }
}
