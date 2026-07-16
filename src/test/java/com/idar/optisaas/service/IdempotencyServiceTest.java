package com.idar.optisaas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idar.optisaas.dto.PaymentRequest;
import com.idar.optisaas.entity.IdempotencyKey;
import com.idar.optisaas.exception.ConflictException;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.IdempotencyScope;
import com.idar.optisaas.util.IdempotencyStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private IdempotencyStore store;
    private IdempotencyService service;

    private static final Long BRANCH_ID = 10L;
    private static final String KEY = "3f6a1e5c-0000-4000-8000-000000000001";
    private static final IdempotencyScope SCOPE = IdempotencyScope.PAYMENT_ADD;

    @BeforeEach
    void setUp() throws Exception {
        store = mock(IdempotencyStore.class);

        service = new IdempotencyService();
        setField("store", store);
        setField("objectMapper", new ObjectMapper());

        TenantContext.setCurrentBranch(BRANCH_ID);
        when(store.find(any(), any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        var field = IdempotencyService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private PaymentRequest payment(String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal(amount));
        request.setMethod("CASH");
        return request;
    }

    /** La misma huella que calcula el servicio, para simular una llave ya registrada. */
    private String hashOf(Object payload) throws Exception {
        var method = IdempotencyService.class.getDeclaredMethod("hash", Object.class);
        method.setAccessible(true);
        return (String) method.invoke(service, payload);
    }

    private IdempotencyKey entry(IdempotencyStatus status, String requestHash, Long resourceId) {
        IdempotencyKey key = new IdempotencyKey();
        key.setScope(SCOPE);
        key.setKeyValue(KEY);
        key.setBranchId(BRANCH_ID);
        key.setRequestHash(requestHash);
        key.setStatus(status);
        key.setResourceId(resourceId);
        return key;
    }

    @Test
    void runsActionWithoutKeyAndLeavesNoTrace() {
        String result = service.run(SCOPE, null, payment("100"), () -> "hecho", r -> 7L, id -> "repetido");

        assertEquals("hecho", result);
        verifyNoInteractions(store);
    }

    @Test
    void reservesAndCompletesOnSuccess() {
        String result = service.run(SCOPE, KEY, payment("100"), () -> "hecho", r -> 7L, id -> "repetido");

        assertEquals("hecho", result);
        verify(store).reserve(eq(BRANCH_ID), eq(SCOPE), eq(KEY), any());
        verify(store).complete(BRANCH_ID, SCOPE, KEY, 7L);
        verify(store, never()).release(any(), any(), any());
    }

    @Test
    void replaysCompletedKeyWithoutRunningTheActionAgain() throws Exception {
        // El caso que da sentido a todo esto: el cobro sí se registró, pero la respuesta
        // se perdió en la red y el cajero volvió a dar clic.
        PaymentRequest request = payment("100");
        when(store.find(BRANCH_ID, SCOPE, KEY))
                .thenReturn(Optional.of(entry(IdempotencyStatus.COMPLETED, hashOf(request), 42L)));

        AtomicInteger executions = new AtomicInteger();
        Function<Long, String> replay = id -> "venta " + id;

        String result = service.run(SCOPE, KEY, request,
                () -> { executions.incrementAndGet(); return "cobro nuevo"; },
                r -> 99L, replay);

        assertEquals("venta 42", result);
        assertEquals(0, executions.get(), "No debe volver a cobrar");
        verify(store, never()).reserve(any(), any(), any(), any());
    }

    @Test
    void rejectsKeyStillInProgress() throws Exception {
        // Doble clic: el primer intento aún no termina.
        PaymentRequest request = payment("100");
        when(store.find(BRANCH_ID, SCOPE, KEY))
                .thenReturn(Optional.of(entry(IdempotencyStatus.IN_PROGRESS, hashOf(request), null)));

        assertThrows(ConflictException.class,
                () -> service.run(SCOPE, KEY, request, () -> "hecho", r -> 7L, id -> "repetido"));
    }

    @Test
    void rejectsSameKeyReusedWithDifferentPayload() throws Exception {
        // Misma llave, otro monto: no es un reintento, es un error del cliente.
        when(store.find(BRANCH_ID, SCOPE, KEY))
                .thenReturn(Optional.of(entry(IdempotencyStatus.COMPLETED, hashOf(payment("100")), 42L)));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.run(SCOPE, KEY, payment("250"), () -> "hecho", r -> 7L, id -> "repetido"));
        assertTrue(ex.getMessage().contains("contenido distinto"));
    }

    @Test
    void releasesKeyWhenTheOperationFails() {
        // Una venta rechazada por stock no debe quemar la llave: el cajero corrige y reintenta.
        RuntimeException failure = new RuntimeException("Stock insuficiente");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.run(SCOPE, KEY, payment("100"), () -> { throw failure; }, r -> 7L, id -> "repetido"));

        assertSame(failure, thrown);
        verify(store).release(BRANCH_ID, SCOPE, KEY);
        verify(store, never()).complete(any(), any(), any(), any());
    }

    @Test
    void requiresASelectedBranch() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class,
                () -> service.run(SCOPE, KEY, payment("100"), () -> "hecho", r -> 7L, id -> "repetido"));
    }
}
