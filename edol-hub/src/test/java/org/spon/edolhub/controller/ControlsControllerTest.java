package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ControlsControllerTest {

    @Mock
    private Model model;

    @InjectMocks
    private ControlsController controller;

    @Test
    @DisplayName("skipObjects returns skip-objects view")
    void returnsSkipObjectsView() {
        String view = controller.skipObjects(model);
        assertThat(view).isEqualTo("dashboard/controls/skip-objects");
    }
}
