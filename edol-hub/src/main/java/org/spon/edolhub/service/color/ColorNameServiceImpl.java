package org.spon.edolhub.service.color;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ColorNameServiceImpl implements ColorNameService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<ColorDefinition> colors;

    public static void main(String[] args) {
        ColorNameServiceImpl colorNameService = new ColorNameServiceImpl();
        colorNameService.init();
        System.out.println(LocaleContextHolder.getLocale());
        System.out.println(colorNameService.resolveColorName("#AA0000", Locale.of("uk")));
    }

    @PostConstruct
    public void init() {
        try (
                InputStream inputStream =
                        new ClassPathResource(
                                "colors/colors.json"
                        ).getInputStream()
        ) {
            colors = objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {
                    }
            );

            for (ColorDefinition color : colors) {
                int[] rgb = parseHex(color.getHex());

                color.setR(rgb[0]);
                color.setG(rgb[1]);
                color.setB(rgb[2]);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load colors dataset",
                    e
            );
        }
    }

    @Override
    public String resolveColorName(
            String hex,
            Locale locale
    ) {
        if (
                hex == null
                        || !hex.matches("^#[0-9a-fA-F]{6}$")
        ) {
            return "Unknown";
        }

        int[] rgb = parseHex(hex);

        ColorDefinition nearest = getColorDefinition(rgb);

        if (nearest == null) {
            return "Unknown";
        }

        Map<String, String> names =
                nearest.getNames();

        String language =
                locale.getLanguage();

        return Optional.ofNullable(
                names.get(language)
        ).orElseGet(
                () -> names.getOrDefault(
                        "en",
                        nearest.getHex()
                )
        );
    }

    private @Nullable ColorDefinition getColorDefinition(int[] rgb) {
        int targetR = rgb[0];
        int targetG = rgb[1];
        int targetB = rgb[2];

        ColorDefinition nearest = null;

        int nearestDistance = Integer.MAX_VALUE;

        for (ColorDefinition color : colors) {

            int dr = targetR - color.getR();
            int dg = targetG - color.getG();
            int db = targetB - color.getB();

            int distance =
                    dr * dr
                            + dg * dg
                            + db * db;

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = color;
            }

        }
        return nearest;
    }

    private int[] parseHex(String hex) {

        return new int[]{
                Integer.valueOf(
                        hex.substring(1, 3),
                        16
                ),
                Integer.valueOf(
                        hex.substring(3, 5),
                        16
                ),
                Integer.valueOf(
                        hex.substring(5, 7),
                        16
                )
        };

    }

}