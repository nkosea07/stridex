package com.racetracker.service.impl;

import com.opencsv.CSVWriter;
import com.racetracker.exception.ApiException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serializes rows to CSV (via opencsv) or XLSX (via the streaming SXSSF workbook, which keeps
 * only a bounded window of rows in memory).
 */
final class TabularExportWriter {

    private static final int STREAMING_WINDOW = 100;

    private TabularExportWriter() {
    }

    static byte[] csv(List<String[]> rows) {
        try (StringWriter writer = new StringWriter();
             CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeAll(rows);
            csvWriter.flush();
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ApiException("Failed to generate CSV export");
        }
    }

    static byte[] xlsx(String sheetName, Iterable<String[]> rows) {
        java.util.Map<String, Iterable<String[]>> sheets = new java.util.LinkedHashMap<>();
        sheets.put(sheetName, rows);
        return writeXlsx(sheets);
    }

    static byte[] xlsx(java.util.Map<String, List<String[]>> sheets) {
        return writeXlsx(sheets);
    }

    private static byte[] writeXlsx(java.util.Map<String, ? extends Iterable<String[]>> sheets) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            for (java.util.Map.Entry<String, ? extends Iterable<String[]>> entry : sheets.entrySet()) {
                Sheet sheet = workbook.createSheet(entry.getKey());
                int rowIndex = 0;
                for (String[] values : entry.getValue()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int column = 0; column < values.length; column++) {
                        row.createCell(column).setCellValue(values[column] == null ? "" : values[column]);
                    }
                }
            }
            workbook.write(baos);
            workbook.dispose();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new ApiException("Failed to generate Excel export");
        }
    }
}
