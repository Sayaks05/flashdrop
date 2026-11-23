package com.flashdrop.catalog;

import java.math.BigDecimal;
import java.time.Instant;

// Parameter object: one named bundle instead of 7 positional arguments (see learning notes Q30).
// A record auto-generates the constructor, accessors (request.name(), not getName()), equals and toString.
public record CreateDropRequest(
        Long productId,
        String name,
        BigDecimal price,
        Instant startsAt,
        Instant endsAt,
        int maxPerUser,
        int totalUnits
) {
}
