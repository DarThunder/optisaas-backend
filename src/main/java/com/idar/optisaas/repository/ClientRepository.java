package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByOwnerIdOrderByFullNameAsc(Long ownerId);

    List<Client> findByOwnerIdAndFullNameContainingIgnoreCaseOrOwnerIdAndEmailContainingIgnoreCaseOrOwnerIdAndPhoneContainingIgnoreCase(
        Long ownerId1, String name,
        Long ownerId2, String email,
        Long ownerId3, String phone
    );

    List<Client> findByOwnerIdIsNullAndBranchIdIn(Collection<Long> branchIds);
}