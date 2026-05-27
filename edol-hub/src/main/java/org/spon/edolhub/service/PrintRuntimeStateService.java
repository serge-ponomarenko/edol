package org.spon.edolhub.service;


import lombok.Getter;
import lombok.Setter;
import org.spon.edolhub.model.entity.PrintJob;
import org.springframework.stereotype.Service;

@Service
@Getter
@Setter
public class PrintRuntimeStateService {

    private volatile boolean allocationPreviewReady;

    private PrintJob currentJob;

}
