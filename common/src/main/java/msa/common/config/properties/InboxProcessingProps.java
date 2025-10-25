package msa.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inbox.processing")
public record InboxProcessingProps(
        int errorMaxLength
) {}
