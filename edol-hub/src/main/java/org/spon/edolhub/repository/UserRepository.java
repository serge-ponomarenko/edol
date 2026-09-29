package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIssuerAndSubject(String issuer, String subject);
}
