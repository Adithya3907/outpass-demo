package com.psgitech.outpass.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Tiny CSV writer: quotes fields, and neutralises spreadsheet formulas (=, +, -, @) in text. */
public final class Csv {
    private Csv() {}

    public static String row(Object... cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            b.append(cell(cells[i]));
        }
        return b.append("\r\n").toString();
    }

    private static String cell(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("^\\+?[0-9][0-9 \\-]+$")) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /** UTF-8 with BOM so Excel shows every character correctly. */
    public static byte[] bytes(List<String> lines) {
        StringBuilder b = new StringBuilder("\uFEFF");
        lines.forEach(b::append);
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }
}
