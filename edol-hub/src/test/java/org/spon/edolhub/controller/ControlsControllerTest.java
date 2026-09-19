package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ControlsControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private Model model;

    @InjectMocks
    private ControlsController controller;

    @Test
    @DisplayName("skipObjects returns skip-objects view")
    void returnsSkipObjectsView() {
        String view = controller.skipObjects(PRINTER_ID, model);
        assertThat(view).isEqualTo("dashboard/controls/skip-objects");
    }
}
