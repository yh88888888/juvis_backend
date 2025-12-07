package com.juvis.juvis.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;    

// storage.* properties 바인딩
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "storage")
public class StorageProps {
    // @NotBlank
    private String bucket;
    // @Min(60)
    // @Max(3600)
    // private int presignExpSeconds = 900;  
 // Presigned TTL
}
