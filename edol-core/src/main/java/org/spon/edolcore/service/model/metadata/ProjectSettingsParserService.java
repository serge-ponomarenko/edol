package org.spon.edolcore.service.model.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spon.edol.model.Filament;
import org.spon.edolcore.exception.ProjectSettingsParseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectSettingsParserService {

    private final ObjectMapper mapper = new ObjectMapper();

    public void enrichFilaments(Path configPath, List<Filament> filaments, List<Integer> amsMapping) {
        try {
            JsonNode root = mapper.readTree(configPath.toFile());

            JsonNode ids = root.get("filament_ids");
            JsonNode vendors = root.get("filament_vendor");
            JsonNode fullIds = root.get("filament_settings_id");

            if (ids == null || vendors == null || fullIds == null)
                return;

            List<String> vendorList = new ArrayList<>();
            List<String> fullIdList = new ArrayList<>();

            for (int i = 0; i < ids.size(); i++) {
                String vendor = vendors.get(i).asText();
                String fullId = fullIds.get(i).asText().split("@")[0].strip();

                vendorList.add(vendor);
                fullIdList.add(fullId);
            }

            // Enrich existing filament objects
            for (Filament filament : filaments) {

                String vendor = vendorList.get(filament.getId() - 1);

                if (vendor != null)
                    filament.setVendor(vendor);

                String fullId = fullIdList.get(filament.getId() - 1);

                if (fullId != null)
                    filament.setFullId(fullId);

                if (amsMapping != null) {
                    Integer amsSlot = amsMapping.get(filament.getId() - 1);
                    if (amsSlot != null)
                        filament.setAmsSlot(amsSlot);
                }
            }

        } catch (IOException e) {
            throw new ProjectSettingsParseException(e);
        }
    }
}
