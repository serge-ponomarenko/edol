package org.spon.edolhub.service.color;

import java.util.Locale;

public interface ColorNameService {

    String resolveColorName(
            String hex,
            Locale locale
    );

}