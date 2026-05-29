package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.color.ColorNameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/colors")
@RequiredArgsConstructor
public class ColorController {

    private static final Locale LOCALE = Locale.of("en");

    private final ColorNameService colorNameService;

    @GetMapping("/resolve")
    public ColorResolveResponse resolveColor(
            @RequestParam String hex
    ) {
        return new ColorResolveResponse(
                colorNameService.resolveColorName(
                        hex,
                        LOCALE
                )
        );
    }

    public record ColorResolveResponse(
            String name
    ) {
    }

}

