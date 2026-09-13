package com.tuitionnetwork.ingestion.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Clean, zero-dependency parser and generator for Microsoft Excel (.xlsx) files.
 */
public final class XlsxParser {

    private XlsxParser() {
    }

    public static List<List<String>> parse(InputStream inputStream) {
        try {
            Map<String, byte[]> zipEntries = new HashMap<>();
            try (ZipInputStream zis = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        zipEntries.put(entry.getName(), zis.readAllBytes());
                    }
                    zis.closeEntry();
                }
            }

            if (zipEntries.isEmpty()) {
                throw new IllegalArgumentException("Invalid XLSX file: archive contains no files");
            }

            // 1. Parse shared strings table if present
            List<String> sharedStrings = new ArrayList<>();
            byte[] sstBytes = zipEntries.get("xl/sharedStrings.xml");
            if (sstBytes != null) {
                sharedStrings = parseSharedStrings(sstBytes);
            }

            // 2. Locate first worksheet (sheet1.xml or any sheet under xl/worksheets/)
            byte[] sheetBytes = zipEntries.get("xl/worksheets/sheet1.xml");
            if (sheetBytes == null) {
                for (Map.Entry<String, byte[]> e : zipEntries.entrySet()) {
                    if (e.getKey().startsWith("xl/worksheets/") && e.getKey().endsWith(".xml")) {
                        sheetBytes = e.getValue();
                        break;
                    }
                }
            }

            if (sheetBytes == null) {
                throw new IllegalArgumentException("No worksheet found in XLSX archive");
            }

            return parseWorksheet(sheetBytes, sharedStrings);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XLSX file: " + e.getMessage(), e);
        }
    }

    private static List<String> parseSharedStrings(byte[] xmlBytes) throws Exception {
        List<String> strings = new ArrayList<>();
        DocumentBuilder db = createSecureDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xmlBytes));
        NodeList siList = doc.getElementsByTagName("si");

        for (int i = 0; i < siList.getLength(); i++) {
            Element si = (Element) siList.item(i);
            StringBuilder sb = new StringBuilder();
            NodeList tList = si.getElementsByTagName("t");
            for (int j = 0; j < tList.getLength(); j++) {
                sb.append(tList.item(j).getTextContent());
            }
            strings.add(sb.toString());
        }
        return strings;
    }

    private static List<List<String>> parseWorksheet(byte[] xmlBytes, List<String> sharedStrings) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        DocumentBuilder db = createSecureDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xmlBytes));
        NodeList rowList = doc.getElementsByTagName("row");

        for (int i = 0; i < rowList.getLength(); i++) {
            Element rowEl = (Element) rowList.item(i);
            NodeList cList = rowEl.getElementsByTagName("c");
            if (cList.getLength() == 0) {
                continue;
            }

            Map<Integer, String> cellMap = new HashMap<>();
            int maxCol = -1;

            for (int j = 0; j < cList.getLength(); j++) {
                Element c = (Element) cList.item(j);
                String cellRef = c.getAttribute("r");
                int colIndex = getColumnIndex(cellRef, j);
                if (colIndex > maxCol) {
                    maxCol = colIndex;
                }

                String type = c.getAttribute("t");
                String value = "";

                if ("s".equals(type)) {
                    NodeList vList = c.getElementsByTagName("v");
                    if (vList.getLength() > 0) {
                        int sstIndex = Integer.parseInt(vList.item(0).getTextContent().trim());
                        if (sstIndex >= 0 && sstIndex < sharedStrings.size()) {
                            value = sharedStrings.get(sstIndex);
                        }
                    }
                } else if ("inlineStr".equals(type)) {
                    NodeList tList = c.getElementsByTagName("t");
                    if (tList.getLength() > 0) {
                        value = tList.item(0).getTextContent();
                    }
                } else {
                    NodeList vList = c.getElementsByTagName("v");
                    if (vList.getLength() > 0) {
                        value = vList.item(0).getTextContent().trim();
                    }
                }

                cellMap.put(colIndex, value);
            }

            List<String> row = new ArrayList<>(maxCol + 1);
            for (int c = 0; c <= maxCol; c++) {
                row.add(cellMap.getOrDefault(c, ""));
            }
            rows.add(row);
        }
        return rows;
    }

    private static int getColumnIndex(String cellRef, int fallbackIndex) {
        if (cellRef == null || cellRef.isBlank()) {
            return fallbackIndex;
        }
        int col = 0;
        int i = 0;
        while (i < cellRef.length() && Character.isLetter(cellRef.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(cellRef.charAt(i)) - 'A' + 1);
            i++;
        }
        return col > 0 ? col - 1 : fallbackIndex;
    }

    private static DocumentBuilder createSecureDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return dbf.newDocumentBuilder();
    }

    /**
     * Creates a valid minimal XLSX byte array from a list of rows.
     */
    public static byte[] createMinimalXlsx(List<List<String>> rows) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // 1. [Content_Types].xml
                zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                          <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
                        </Types>
                        """.trim().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 2. _rels/.rels
                zos.putNextEntry(new ZipEntry("_rels/.rels"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """.trim().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 3. xl/_rels/workbook.xml.rels
                zos.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
                        </Relationships>
                        """.trim().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 4. xl/workbook.xml
                zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <sheets>
                            <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
                          </sheets>
                        </workbook>
                        """.trim().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Collect unique shared strings
                List<String> stringTable = new ArrayList<>();
                Map<String, Integer> stringIndexMap = new HashMap<>();

                for (List<String> row : rows) {
                    for (String cell : row) {
                        if (cell != null && !stringIndexMap.containsKey(cell)) {
                            stringIndexMap.put(cell, stringTable.size());
                            stringTable.add(cell);
                        }
                    }
                }

                // 5. xl/sharedStrings.xml
                zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
                StringBuilder sstBuilder = new StringBuilder();
                sstBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
                sstBuilder.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
                        .append(stringTable.size()).append("\" uniqueCount=\"").append(stringTable.size()).append("\">\n");
                for (String str : stringTable) {
                    sstBuilder.append("  <si><t>").append(escapeXml(str)).append("</t></si>\n");
                }
                sstBuilder.append("</sst>");
                zos.write(sstBuilder.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 6. xl/worksheets/sheet1.xml
                zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
                StringBuilder sheetBuilder = new StringBuilder();
                sheetBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
                sheetBuilder.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
                sheetBuilder.append("  <sheetData>\n");

                for (int r = 0; r < rows.size(); r++) {
                    List<String> row = rows.get(r);
                    sheetBuilder.append("    <row r=\"").append(r + 1).append("\">\n");
                    for (int c = 0; c < row.size(); c++) {
                        String cellVal = row.get(c);
                        if (cellVal != null) {
                            int sstIndex = stringIndexMap.get(cellVal);
                            String cellRef = indexToColumnName(c) + (r + 1);
                            sheetBuilder.append("      <c r=\"").append(cellRef).append("\" t=\"s\"><v>")
                                    .append(sstIndex).append("</v></c>\n");
                        }
                    }
                    sheetBuilder.append("    </row>\n");
                }
                sheetBuilder.append("  </sheetData>\n");
                sheetBuilder.append("</worksheet>");
                zos.write(sheetBuilder.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create minimal XLSX: " + e.getMessage(), e);
        }
    }

    private static String indexToColumnName(int index) {
        StringBuilder sb = new StringBuilder();
        index++;
        while (index > 0) {
            int rem = (index - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            index = (index - 1) / 26;
        }
        return sb.toString();
    }

    private static String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
