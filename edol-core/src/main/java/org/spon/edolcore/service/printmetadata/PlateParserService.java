package org.spon.edolcore.service.printmetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.spon.edol.model.BoundingBox;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class PlateParserService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<BoundingBox> parse(Path file) {
        try {
            PlateFile plate = objectMapper.readValue(file.toFile(), PlateFile.class);

            return plate.getBbox_objects().stream()
                    .filter(plateObject -> !plateObject.getName().equals("wipe_tower"))     // skip Wipe tower
                    .map(o -> new BoundingBox(
                                    o.getBbox().get(0),
                                    o.getBbox().get(1),
                                    o.getBbox().get(2),
                                    o.getBbox().get(3)
                            )
                    ).toList();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse plate file", e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlateFile {
        private List<PlateObject> bbox_objects;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlateObject {
        private int id;
        private String name;
        private List<Double> bbox;
    }

}
