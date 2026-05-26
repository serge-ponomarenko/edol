package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.PrintAllocationItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintAllocationItemRepository
        extends JpaRepository<PrintAllocationItem, Long> {
}
