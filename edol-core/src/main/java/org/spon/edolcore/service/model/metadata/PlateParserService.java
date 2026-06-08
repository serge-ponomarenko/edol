package org.spon.edolcore.service.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.spon.edol.model.BoundingBox;
import org.spon.edolcore.exception.PlateParseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public class PlateParserService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<BoundingBox> parse(Path file) {
        try {
            PlateFile plate = objectMapper.readValue(file.toFile(), PlateFile.class);

            return plate.getBboxObjects().stream()
                    .filter(plateObject -> !plateObject.getName().equals("wipe_tower"))     // skip Wipe tower
                    .map(o -> new BoundingBox(
                                    o.getBbox().getFirst(),
                                    o.getBbox().get(1),
                                    o.getBbox().get(2),
                                    o.getBbox().get(3)
                            )
                    ).toList();

        } catch (IOException e) {
            throw new PlateParseException(e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlateFile {
        @JsonProperty("bbox_objects")
        private List<PlateObject> bboxObjects;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlateObject {
        private int id;
        private String name;
        private List<Double> bbox;
    }

}
