package org.spon.edolhub.common.uuid;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidV7Generator implements UuidGenerator {

    @Override
    public UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }

}
