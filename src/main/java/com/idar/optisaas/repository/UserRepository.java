package com.idar.optisaas.repository;

import com.idar.optisaas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Este es el que ya tenías
    Optional<User> findByEmailOrUsername(String email, String username);
    
    // Este también lo tenías
    Optional<User> findByEmail(String email);

    // --- AGREGA ESTA LÍNEA QUE FALTABA ---
    Optional<User> findByUsername(String username);
}