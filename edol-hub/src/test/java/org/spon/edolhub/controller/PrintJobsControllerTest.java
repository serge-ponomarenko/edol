package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.repository.PrintJobRepository;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PrintJobsControllerTest {

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private Model model;

    @InjectMocks
    private PrintJobsController controller;

    @Nested
    @DisplayName("list")
    class List {

        @Test
        @DisplayName("returns list view")
        void returnsListView() {
            String view = controller.list(model);
            assertThat(view).isEqualTo("dashboard/print-jobs/list");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes job and redirects")
        void deletesAndRedirects() {
            String view = controller.delete(1L);
            assertThat(view).isEqualTo("redirect:/print-jobs");
            verify(printJobRepository).deleteById(1L);
        }
    }
}
