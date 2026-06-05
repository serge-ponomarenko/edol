package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.service.color.ColorNameService;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColorControllerTest {

    @Mock
    private ColorNameService colorNameService;

    @InjectMocks
    private ColorController controller;

    @Nested
    @DisplayName("resolveColor")
    class ResolveColor {

        @Test
        @DisplayName("returns resolved color name")
        void returnsColorName() {
            when(colorNameService.resolveColorName(eq("#F95D73"), any(Locale.class)))
                    .thenReturn("Rose");

            ColorController.ColorResolveResponse response = controller.resolveColor("#F95D73");

            assertThat(response.name()).isEqualTo("Rose");
        }
    }
}
