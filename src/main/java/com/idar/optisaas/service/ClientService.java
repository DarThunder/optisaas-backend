package com.idar.optisaas.service;

import com.idar.optisaas.entity.Client;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.ClientRepository;
import com.idar.optisaas.repository.UserBranchRoleRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClientService {

    @Autowired private ClientRepository clientRepository;
    @Autowired private UserBranchRoleRepository roleRepository;

    @Transactional
    public List<Client> getAllClients() {
        Long ownerId = getCurrentOwnerId();
        backfillLegacyClients(ownerId);
        return clientRepository.findByOwnerIdOrderByFullNameAsc(ownerId);
    }

    @Transactional
    public List<Client> searchClients(String query) {
        Long ownerId = getCurrentOwnerId();
        backfillLegacyClients(ownerId);
        return clientRepository.findByOwnerIdAndFullNameContainingIgnoreCaseOrOwnerIdAndEmailContainingIgnoreCaseOrOwnerIdAndPhoneContainingIgnoreCase(
            ownerId, query, ownerId, query, ownerId, query
        );
    }

    public Client createClient(Client client) {
        Long branchId = getCurrentBranchId();
        Long ownerId = getCurrentOwnerId();

        client.setBranchId(branchId);
        client.setOwnerId(ownerId);
        return clientRepository.save(client);
    }

    public Client updateClient(Long id, Client updatedData) {
        Client existing = getClientForCurrentOwner(id);

        existing.setFullName(updatedData.getFullName());
        existing.setPhone(updatedData.getPhone());
        existing.setEmail(updatedData.getEmail());
        existing.setOccupation(updatedData.getOccupation());
        existing.setAge(updatedData.getAge());

        return clientRepository.save(existing);
    }

    public void deleteClient(Long id) {
        Client existing = getClientForCurrentOwner(id);
        clientRepository.delete(existing);
    }

    public Client getClientForCurrentOwner(Long id) {
        Long ownerId = getCurrentOwnerId();
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (client.getOwnerId() == null) {
            Long legacyOwnerId = resolveOwnerIdForBranch(client.getBranchId());
            client.setOwnerId(legacyOwnerId);
            clientRepository.save(client);
        }

        if (!ownerId.equals(client.getOwnerId())) {
            throw new RuntimeException("No tienes permiso para acceder a este cliente");
        }

        return client;
    }

    private Long getCurrentBranchId() {
        Long branchId = TenantContext.getCurrentBranch();
        if (branchId == null) {
            throw new RuntimeException("Selecciona una sucursal antes de gestionar clientes");
        }
        return branchId;
    }

    private Long getCurrentOwnerId() {
        return resolveOwnerIdForBranch(getCurrentBranchId());
    }

    private Long resolveOwnerIdForBranch(Long branchId) {
        UserBranchRole ownerRole = roleRepository.findFirstByBranch_IdAndRole(branchId, Role.OWNER)
                .orElseThrow(() -> new RuntimeException("La sucursal no tiene OWNER asignado"));
        return ownerRole.getUser().getId();
    }

    private void backfillLegacyClients(Long ownerId) {
        List<Long> branchIds = roleRepository.findByUser_IdAndRole(ownerId, Role.OWNER).stream()
                .map(role -> role.getBranch().getId())
                .collect(Collectors.toList());

        if (branchIds.isEmpty()) {
            return;
        }

        List<Client> legacyClients = clientRepository.findByOwnerIdIsNullAndBranchIdIn(branchIds);
        legacyClients.forEach(client -> client.setOwnerId(ownerId));
        clientRepository.saveAll(legacyClients);
    }
}