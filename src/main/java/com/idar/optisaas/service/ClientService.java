package com.idar.optisaas.service;

import com.idar.optisaas.entity.Client;
import com.idar.optisaas.repository.ClientRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {

    @Autowired private ClientRepository clientRepository;

    public List<Client> getAllClients() {
        Long branchId = TenantContext.getCurrentBranch();
        return clientRepository.findByBranchId(branchId);
    }

    public List<Client> searchClients(String query) {
        Long branchId = TenantContext.getCurrentBranch();
        // Buscamos coincidencia en nombre, email o teléfono, siempre restringido a la sucursal actual
        return clientRepository.findByBranchIdAndFullNameContainingIgnoreCaseOrBranchIdAndEmailContainingIgnoreCaseOrBranchIdAndPhoneContainingIgnoreCase(
            branchId, query, branchId, query, branchId, query
        );
    }

    public Client createClient(Client client) {
        client.setBranchId(TenantContext.getCurrentBranch());
        // Aquí podrías validar si el email ya existe, etc.
        return clientRepository.save(client);
    }

    public Client updateClient(Long id, Client updatedData) {
        Client existing = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        
        // Seguridad: Verificar que el cliente pertenece a la sucursal actual
        if (!existing.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso para editar este cliente");
        }

        existing.setFullName(updatedData.getFullName());
        existing.setPhone(updatedData.getPhone());
        existing.setEmail(updatedData.getEmail());
        existing.setOccupation(updatedData.getOccupation());
        existing.setAge(updatedData.getAge());
        // No actualizamos createdAt ni branchId
        
        return clientRepository.save(existing);
    }

    public void deleteClient(Long id) {
        Client existing = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
                
        if (!existing.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso para eliminar este cliente");
        }
        
        clientRepository.deleteById(id);
    }
}