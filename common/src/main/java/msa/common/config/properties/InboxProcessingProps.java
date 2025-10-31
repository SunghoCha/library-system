package msa.common.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inbox.processing")
public record InboxProcessingProps(
        @Min(4) int errorMaxLength // Apache abbreviate가 최소 4이상 길이 요구함. 에러메시지 길이가 4미만이면 애초에 이상 케이스
) {}
