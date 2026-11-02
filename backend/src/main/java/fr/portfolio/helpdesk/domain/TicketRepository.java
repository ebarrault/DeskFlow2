package fr.portfolio.helpdesk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findBySlaBreachedFalseAndStatusIn(Collection<TicketStatus> statuses);

    long countByStatusIn(Collection<TicketStatus> statuses);

    long countByStatusInAndSlaBreachedTrue(Collection<TicketStatus> statuses);

    @Query("select t.status, count(t) from Ticket t group by t.status")
    List<Object[]> countGroupByStatus();

    @Query("select t.priority, count(t) from Ticket t where t.status in :statuses group by t.priority")
    List<Object[]> countActiveGroupByPriority(Collection<TicketStatus> statuses);

    @Query("select coalesce(a.name, 'Non affecté'), count(t) from Ticket t left join t.assignee a where t.status in :statuses group by a.name")
    List<Object[]> countActiveGroupByAssignee(Collection<TicketStatus> statuses);

    List<Ticket> findByCreatedAtAfter(Instant since);

    List<Ticket> findByResolvedAtAfter(Instant since);
}
