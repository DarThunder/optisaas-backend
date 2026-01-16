package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    // Buscar todos los clientes de una sucursal específica
    List<Client> findByBranchId(Long branchId);

    // Buscador: Encuentra por nombre, email o teléfono dentro de una sucursal
    // Nota: Es importante filtrar por branchId para no ver clientes de otras tiendas
    List<Client> findByBranchIdAndFullNameContainingIgnoreCaseOrBranchIdAndEmailContainingIgnoreCaseOrBranchIdAndPhoneContainingIgnoreCase(
        Long branchId1, String name, 
        Long branchId2, String email, 
        Long branchId3, String phone
    );
}