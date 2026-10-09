package com.racetracker.rfidreaderimpinj.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record NormalizedRead(
    String sourceEventId,
    String rfidTag,
    OffsetDateTime readAt,
    String antenna,
    BigDecimal rssi,
    String rawPayload
) {
    public Map<String, Object> toGatewayMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            values.put("sourceEventId", sourceEventId);
        }
        values.put("rfidTag", rfidTag);
        values.put("readAt", readAt.toString());
        if (antenna != null && !antenna.isBlank()) {
            values.put("antenna", antenna);
        }
        if (rssi != null) {
            values.put("rssi", rssi);
        }
        if (rawPayload != null && !rawPayload.isBlank()) {
            values.put("rawPayload", rawPayload);
        }
        return values;
    }
}
