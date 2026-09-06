package com.jxl.ai.intelliconf.author_discovery.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenAlexAuthor {

    private String id;
    private String displayName;
    private String institution;
    private String countryCode;
    private boolean corresponding;
}
