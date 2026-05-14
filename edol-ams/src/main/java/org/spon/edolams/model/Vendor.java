package org.spon.edolams.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    private Long id;

    private String name;

    private String website;

}