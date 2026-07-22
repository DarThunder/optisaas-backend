package com.idar.optisaas.repository;

import com.idar.optisaas.entity.RegistrationRequest;
import com.idar.optisaas.util.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    /** Las pendientes van primero las más viejas: ese es el orden en que hay que atenderlas. */
    List<RegistrationRequest> findByStatusOrderByCreatedAtAsc(RegistrationStatus status);

    List<RegistrationRequest> findAllByOrderByCreatedAtDesc();

    /**
     * Sirve para no acumular filas repetidas cuando alguien envía el formulario tres veces
     * porque no vio confirmación. La segunda vez se responde igual, sin crear otra solicitud.
     */
    Optional<RegistrationRequest> findFirstByEmailIgnoreCaseAndStatus(String email, RegistrationStatus status);
}
