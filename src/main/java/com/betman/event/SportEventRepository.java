package com.betman.event;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SportEventRepository extends JpaRepository<SportEvent, Long> {

	Page<SportEvent> findByStatus(EventStatus status, Pageable pageable);

	List<SportEvent> findByStatusAndStartsAtLessThanEqual(EventStatus status, Instant threshold);

	long countByStatus(EventStatus status);
}
