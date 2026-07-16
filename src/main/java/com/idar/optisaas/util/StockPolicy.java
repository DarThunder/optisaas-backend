package com.idar.optisaas.util;

/**
 * Qué mercancía descuenta y reingresa inventario.
 *
 * Armazones y accesorios son piezas físicas del anaquel: se descuentan al vender y vuelven al
 * devolver. Los lentes se fabrican bajo receta y los servicios no son inventariables, así que
 * ni se descuentan ni se reingresan. La venta y la devolución DEBEN usar la misma regla, o el
 * stock se desbalancea.
 */
public final class StockPolicy {

    private StockPolicy() {}

    public static boolean managesStock(ProductType type) {
        return type == ProductType.FRAME || type == ProductType.ACCESSORY;
    }
}
