package com.pocketstock.ledger.dart;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dart.api")
@Getter
@Setter
public class OpenDartProperties {

    private String key;
    private String baseUrl;
}
