package fr.portfolio.helpdesk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByActiveTrueAndRoleInOrderByNameAsc(Collection<Role> roles);

    List<User> findAllByOrderByNameAsc();
}
