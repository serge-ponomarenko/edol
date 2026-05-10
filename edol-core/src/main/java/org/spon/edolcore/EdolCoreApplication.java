package org.spon.edolcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EdolCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdolCoreApplication.class, args);
    }

}
