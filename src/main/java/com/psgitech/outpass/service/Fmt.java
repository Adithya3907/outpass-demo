package com.psgitech.outpass.service;

import com.psgitech.outpass.config.AppConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Template formatting helpers, used in Thymeleaf as ${@fmt.dt(x)}. All times are shown in IST. */
@Component("fmt")
public class Fmt {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    public String dt(Instant i) { return i == null ? "-" : DT.format(i.atZone(AppConfig.ZONE)); }
    public String hm(Instant i) { return i == null ? "-" : T.format(i.atZone(AppConfig.ZONE)); }
    public String date(LocalDate d) { return d == null ? "-" : D.format(d); }
    public String time(LocalTime t) { return t == null ? "-" : T.format(t); }
    public String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] p = name.trim().split("\\s+");
        String s = p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "");
        return s.toUpperCase(Locale.ROOT);
    }
}
