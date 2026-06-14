package org.spon.edolcore.service.printer.runtime;

import lombok.Getter;
import lombok.Setter;
import org.spon.edol.model.CameraSnapshot;

import java.nio.file.Path;
import java.util.LinkedList;

@Getter
@Setter
public class CameraRuntimeState {

    private CameraSnapshot latest;

    private Path latestSnapshotFile;

    private final LinkedList<CameraSnapshot> history =
            new LinkedList<>();

    private String currentSessionId = "default";
}