package com.service;

import com.service.WeeklyReservations.Reservation;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyReservationsTest {
    final LocalDate monday = BackendRegressionTest.MONDAY;
    final LocalTime start = LocalTime.of(7, 0), end = LocalTime.of(8, 0);

    @Test void rollbackRemovesAllTentativeIndexesAndKeepsTheEarlierReservations() {
        var book = new WeeklyReservations();
        book.add(new Reservation("GV", "OTHER", "R", monday, start, end, null, null, false, false));
        int original = book.size();
        book.add(new Reservation("GV", "A", "LAB", monday.plusDays(1), start, end, "P", "G", true, true));
        int firstPractice = book.size();
        book.add(new Reservation("GV", "A", "LAB", monday.plusDays(8), start, end, "P", "G", true, true));
        book.rollback(firstPractice);
        assertTrue(book.differentSessionDayBlocked("GV", monday.plusDays(15), false));
        assertFalse(book.roomBusy("A", "LAB", monday.plusDays(8), start, end));
        book.rollback(original);
        assertFalse(book.differentSessionDayBlocked("GV", monday.plusDays(15), false));
        assertFalse(book.teacherBusy("GV", monday.plusDays(1), start, end));
        assertFalse(book.studentsBusy("A", "P", "G", true, monday.plusDays(1), start, end));
        assertTrue(book.teacherBusy("GV", monday, start, end));
        assertEquals(original, book.size());
    }

    @Test void sameClassTheoryBlocksBothPracticeGroupsButDifferentGroupsCanOverlap() {
        var book = new WeeklyReservations();
        book.add(new Reservation("A", "S", "LAB", monday, start, end, "CLASS", "GROUP-1", true, true));
        assertFalse(book.studentsBusy("S", "CLASS", "GROUP-2", true, monday, start, end));
        assertTrue(book.studentsBusy("S", "CLASS", "LT", false, monday, start, end));
        assertFalse(book.studentsBusy("OTHER", "CLASS", "LT", false, monday, start, end));
        book.add(new Reservation("B", "S", "LT", monday, start, end, "CLASS", "THEORY", false, true));
        assertTrue(book.studentsBusy("S", "CLASS", "GROUP-2", true, monday, start, end));
        assertFalse(book.studentsBusy("S", "CLASS", "GROUP-2", true, monday, end, end.plusHours(1)));
    }

    @Test void actualDaysAcrossSchoolsAndRoomIdentitiesKeepTheirConflictRules() {
        var book = new WeeklyReservations();
        book.add(new Reservation("GV", "OTHER", "Lab-A", monday, start, end, null, null, true, false));
        assertTrue(book.differentSessionDayBlocked("GV", monday, false));
        assertFalse(book.differentSessionDayBlocked("GV", monday.plusWeeks(1), false));
        assertTrue(book.roomBusy("OTHER", "LAB-A", monday, start, end));
        assertFalse(book.roomBusy("S", "LAB-A", monday, start, end));
        assertFalse(book.roomBusy("OTHER", "LAB-A", monday, end, end.plusHours(1)));
    }
}
