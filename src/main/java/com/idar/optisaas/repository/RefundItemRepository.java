package com.idar.optisaas.repository;

import com.idar.optisaas.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {

    /** Todo lo ya devuelto de una venta, para saber cuánto queda devolvible de cada partida. */
    @Query("SELECT ri FROM RefundItem ri WHERE ri.refund.sale.id = :saleId")
    List<RefundItem> findBySaleId(@Param("saleId") Long saleId);
}
