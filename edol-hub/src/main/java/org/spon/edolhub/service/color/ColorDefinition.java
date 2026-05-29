package org.spon.edolhub.service.color;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ColorDefinition {

    private String hex;

    private Map<String, String> names;

    private int r;

    private int g;

    private int b;

}
