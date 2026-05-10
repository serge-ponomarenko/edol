package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printmetadata.ModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/model")
public class ModelController {

    private final ModelService modelService;

    @GetMapping("/download")
    public String download() throws Exception {
        return modelService.fetchMetadata().toString();
    }
}
