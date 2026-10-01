package com.sentrifugo.pms.cycle;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Endpoint #8: {@code GET /pms/cycles/export} — same filters as the list, no paging. */
@Component
public class CycleCsvExporter {

    private static final String[] HEADERS = {
            "Cycle ID", "Cycle Name", "Type", "Period Start", "Period End", "Applicable To", "Created On", "Status",
    };

    public byte[] toCsv(List<CycleDtos.ListItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append("\r\n");
        for (CycleDtos.ListItem item : items) {
            sb.append(field(item.cycleCode())).append(',')
                    .append(field(item.name())).append(',')
                    .append(field(item.type())).append(',')
                    .append(field(String.valueOf(item.periodStart()))).append(',')
                    .append(field(String.valueOf(item.periodEnd()))).append(',')
                    .append(field(item.applicableTo())).append(',')
                    .append(field(String.valueOf(item.createdOn()))).append(',')
                    .append(field(item.status()))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String field(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
