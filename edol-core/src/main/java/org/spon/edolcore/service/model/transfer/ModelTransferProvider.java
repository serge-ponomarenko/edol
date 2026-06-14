package org.spon.edolcore.service.model.transfer;

import java.nio.file.Path;
import java.util.UUID;

public interface ModelTransferProvider {

    Path MODELS_DIR = Path.of("models");

    void requestModel(UUID printerId);

}
