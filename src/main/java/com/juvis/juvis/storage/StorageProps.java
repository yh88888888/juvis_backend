package com.juvis.juvis.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;    

// storage.* properties 바인딩
@Getter @Setter
@Validated
@ConfigurationProperties(prefix = "app.s3")
public class StorageProps {
    @NotBlank
    private String bucket;

    private String prefix = "maintenance";
}
