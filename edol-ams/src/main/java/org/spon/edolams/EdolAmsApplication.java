package org.spon.edolams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EdolAmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdolAmsApplication.class, args);
    }

}
