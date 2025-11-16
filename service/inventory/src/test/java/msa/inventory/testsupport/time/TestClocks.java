package msa.inventory.testsupport.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class TestClocks {
    private TestClocks() {}
    public static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC);

    public static Clock fixedClock(int y, int m, int d, int H, int M, int S) {
        return Clock.fixed(
                LocalDateTime.of(y, m, d, H, M, S).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
    }
}
