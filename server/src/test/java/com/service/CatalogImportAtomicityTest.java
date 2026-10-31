package com.service;

import com.dto.CatalogDTO.InstitutionInput;
import com.dto.CatalogImportDTO.Kind;
import com.repository.InstitutionRepository;
import com.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
class CatalogImportAtomicityTest {
    @Autowired CatalogImportService imports;
    @SpyBean CatalogService catalogs;
    @Autowired InstitutionRepository schools;
    @Autowired RoomRepository rooms;

    @Test void lateFailureRollsBackRowsAlreadyWritten() throws Exception {
        catalogs.createInstitution(new InstitutionInput("IMP_TX", "Atomicity", true, null));
        try {
            var file = CatalogImportIntegrationTest.file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"101", "THEORY", 60}, {"FAIL", "THEORY", 60}});
            var preview = imports.preview("IMP_TX", Kind.ROOMS, file);
            doThrow(new IllegalStateException("Simulated second row failure")).when(catalogs).createRoom(eq("IMP_TX"), argThat(r -> "FAIL".equals(r.roomCode())));
            assertThrows(IllegalStateException.class, () -> imports.commit("IMP_TX", Kind.ROOMS, file, preview.snapshot()));
            assertTrue(rooms.findBySchoolCodeOrderByCapacityAsc("IMP_TX").isEmpty());
        } finally {
            rooms.deleteAll(rooms.findBySchoolCodeOrderByCapacityAsc("IMP_TX")); schools.deleteById("IMP_TX");
        }
    }
}
