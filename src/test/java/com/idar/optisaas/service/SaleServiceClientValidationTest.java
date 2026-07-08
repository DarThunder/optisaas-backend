package com.idar.optisaas.service;

import com.idar.optisaas.dto.SaleRequest;
import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.Client;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SaleServiceClientValidationTest {

    private SaleRepository saleRepository;
    private ProductRepository productRepository;
    private ClientRepository clientRepository;
    private UserRepository userRepository;
    private ClinicalRecordRepository clinicalRepository;
    private BranchRepository branchRepository;
    private UserBranchRoleRepository roleRepository;
    private SaleService saleService;

    private static final Long BRANCH_ID = 10L;
    private static final Long OTHER_BRANCH_ID_SAME_OWNER = 20L;
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_OWNER_ID = 2L;

    @BeforeEach
    void setUp() throws Exception {
        saleRepository = mock(SaleRepository.class);
        productRepository = mock(ProductRepository.class);
        clientRepository = mock(ClientRepository.class);
        userRepository = mock(UserRepository.class);
        clinicalRepository = mock(ClinicalRecordRepository.class);
        branchRepository = mock(BranchRepository.class);
        roleRepository = mock(UserBranchRoleRepository.class);

        saleService = new SaleService();
        setField("saleRepository", saleRepository);
        setField("productRepository", productRepository);
        setField("clientRepository", clientRepository);
        setField("userRepository", userRepository);
        setField("clinicalRepository", clinicalRepository);
        setField("branchRepository", branchRepository);
        setField("roleRepository", roleRepository);

        TenantContext.setCurrentBranch(BRANCH_ID);

        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));

        User seller = new User();
        seller.setId(1L);
        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(seller));

        User owner = new User();
        owner.setId(OWNER_ID);
        UserBranchRole ownerRole = new UserBranchRole();
        ownerRole.setUser(owner);
        when(roleRepository.findFirstByBranch_IdAndRole(BRANCH_ID, Role.OWNER)).thenReturn(Optional.of(ownerRole));

        when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        var field = SaleService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(saleService, value);
    }

    private SaleRequest baseRequest() {
        SaleRequest request = new SaleRequest();
        request.setItems(Collections.emptyList());
        return request;
    }

    @Test
    void allowsSaleWithoutClient_walkIn() {
        SaleRequest request = baseRequest();
        request.setClientId(null);
        request.setQuotation(false);

        assertDoesNotThrow(() -> saleService.createSale(request, "cajero@test.com"));
        verify(clientRepository, never()).findById(any());
    }

    @Test
    void rejectsQuotationWithoutClient() {
        SaleRequest request = baseRequest();
        request.setClientId(null);
        request.setQuotation(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> saleService.createSale(request, "cajero@test.com"));
        assertEquals("Las cotizaciones requieren un cliente", ex.getMessage());
    }

    @Test
    void allowsClientFromAnotherBranchOfTheSameOwner() {
        // Cliente registrado en OTRA sucursal, pero del mismo dueño: debe permitirse
        // (reconocimiento de clientes entre sucursales de la misma cadena).
        Client sameOwnerClient = new Client();
        sameOwnerClient.setId(99L);
        sameOwnerClient.setBranchId(OTHER_BRANCH_ID_SAME_OWNER);
        sameOwnerClient.setOwnerId(OWNER_ID);
        when(clientRepository.findById(99L)).thenReturn(Optional.of(sameOwnerClient));

        SaleRequest request = baseRequest();
        request.setClientId(99L);
        request.setQuotation(false);

        assertDoesNotThrow(() -> saleService.createSale(request, "cajero@test.com"));
    }

    @Test
    void rejectsClientFromAnotherOwnerAccount() {
        // Cliente de OTRA cuenta/dueño en la plataforma: debe rechazarse siempre.
        Client foreignClient = new Client();
        foreignClient.setId(55L);
        foreignClient.setBranchId(30L);
        foreignClient.setOwnerId(OTHER_OWNER_ID);
        when(clientRepository.findById(55L)).thenReturn(Optional.of(foreignClient));

        SaleRequest request = baseRequest();
        request.setClientId(55L);
        request.setQuotation(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> saleService.createSale(request, "cajero@test.com"));
        assertEquals("El cliente no pertenece a tu cuenta", ex.getMessage());
    }

    @Test
    void allowsClientFromSameBranch() {
        Client localClient = new Client();
        localClient.setId(1L);
        localClient.setBranchId(BRANCH_ID);
        localClient.setOwnerId(OWNER_ID);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(localClient));

        SaleRequest request = baseRequest();
        request.setClientId(1L);
        request.setQuotation(false);

        assertDoesNotThrow(() -> saleService.createSale(request, "cajero@test.com"));
    }
}
