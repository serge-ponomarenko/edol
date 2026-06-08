package org.spon.edolcore.service.model.transfer;

import java.nio.file.Path;

public interface ModelTransferProvider {

    Path MODELS_DIR = Path.of("models");

    void requestModel();

}
