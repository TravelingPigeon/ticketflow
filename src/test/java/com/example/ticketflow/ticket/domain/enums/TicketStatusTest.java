package com.example.ticketflow.ticket.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketStatusTest {

    @Test
    void shouldAllowExpectedTransitions() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(
                TicketStatus.PROCESSING
        ));

        assertTrue(TicketStatus.PROCESSING.canTransitionTo(
                TicketStatus.WAITING_CUSTOMER
        ));
        assertTrue(TicketStatus.PROCESSING.canTransitionTo(
                TicketStatus.RESOLVED
        ));

        assertTrue(TicketStatus.WAITING_CUSTOMER.canTransitionTo(
                TicketStatus.PROCESSING
        ));
        assertTrue(TicketStatus.WAITING_CUSTOMER.canTransitionTo(
                TicketStatus.RESOLVED
        ));

        assertTrue(TicketStatus.RESOLVED.canTransitionTo(
                TicketStatus.CLOSED
        ));
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(
                TicketStatus.PROCESSING
        ));
    }

    @Test
    void shouldRejectEveryOtherCombination() {
        Set<String> allowed = Set.of(
                "OPEN->PROCESSING",
                "PROCESSING->WAITING_CUSTOMER",
                "PROCESSING->RESOLVED",
                "WAITING_CUSTOMER->PROCESSING",
                "WAITING_CUSTOMER->RESOLVED",
                "RESOLVED->CLOSED",
                "RESOLVED->PROCESSING"
        );

        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                assertEquals(
                        allowed.contains(from + "->" + to),
                        from.canTransitionTo(to),
                        "流转判定与预期不符：" + from + " -> " + to
                );
            }
        }
    }

    @Test
    void shouldRejectNullTarget() {
        assertFalse(TicketStatus.OPEN.canTransitionTo(null));
    }
}
