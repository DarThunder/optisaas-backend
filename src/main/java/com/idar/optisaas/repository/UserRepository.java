package com.idar.optisaas.repository;

import com.idar.optisaas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByEmailOrUsername(String email, String username);

    List<User> findAllByEmail(String email);

    List<User> findAllByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    /**
     * Busca por correo o usuario tolerando que cualquiera de los dos criterios venga vacío.
     *
     * Como derived query, un argumento nulo se traduce a `email IS NULL OR username IS NULL`,
     * lo que empata con todos los usuarios sin correo: un identificador vacío devolvía varios
     * usuarios y reventaba con IncorrectResultSizeDataAccessException. Aquí los criterios
     * vacíos se descartan antes de consultar y el resultado se acota a uno.
     */
    default Optional<User> findByEmailOrUsername(String email, String username) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasUsername = username != null && !username.isBlank();

        List<User> matches;
        if (hasEmail && hasUsername) {
            matches = findAllByEmailOrUsername(email, username);
        } else if (hasEmail) {
            matches = findAllByEmail(email);
        } else if (hasUsername) {
            matches = findAllByUsername(username);
        } else {
            return Optional.empty();
        }

        // Un mismo texto puede ser el correo de una persona y el usuario de otra. Se prefiere
        // el correo para que la resolución sea estable y no dependa del orden que dé la BD.
        return matches.stream()
                .filter(u -> hasEmail && email.equals(u.getEmail()))
                .findFirst()
                .or(() -> matches.stream().findFirst());
    }
}
