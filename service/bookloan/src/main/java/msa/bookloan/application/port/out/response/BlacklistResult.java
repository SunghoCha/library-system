package msa.bookloan.application.port.out.response;

public record BlacklistResult(
        boolean isBlacklisted,
        String reason
) {
}
