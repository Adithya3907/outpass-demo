package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.Department;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * One-click load of the college files kept in the project's data/ folder (departments.csv, staff.csv,
 * heads.csv, students.csv) using exactly the same rules as the CSV upload in Admin > Users.
 */
@Service
@RequiredArgsConstructor
public class InstitutionImportService {
    private final Repos.Departments departments;
    private final PeopleService people;

    @Transactional
    public PeopleService.Report run(Path dir, String actor) throws IOException {
        PeopleService.Report all = new PeopleService.Report();
        if (!Files.isDirectory(dir)) throw new BusinessException("Data folder not found: " + dir.toAbsolutePath());

        Path dep = dir.resolve("departments.csv");
        if (Files.exists(dep)) {
            for (String line : Files.readAllLines(dep).stream().skip(1).toList()) {
                if (line.isBlank()) continue;
                List<String> c = AdminService.parseCsvLine(line);
                if (c.size() < 2 || c.get(0).isBlank()) continue;
                String code = c.get(0).trim().toUpperCase(Locale.ROOT);
                if (departments.findByCodeIgnoreCase(code).isPresent()) continue;
                Department d = new Department();
                d.setCode(code);
                d.setName(c.get(1).trim());
                departments.save(d);
                all.add("departments created");
            }
        }
        merge(all, "heads.csv", dir, actor, 'h');
        merge(all, "staff.csv", dir, actor, 'f');
        merge(all, "students.csv", dir, actor, 's');
        return all;
    }

    private void merge(PeopleService.Report all, String file, Path dir, String actor, char kind) throws IOException {
        Path f = dir.resolve(file);
        if (!Files.exists(f)) { all.err("File not found (skipped): " + file); return; }
        try (InputStream in = Files.newInputStream(f)) {
            PeopleService.Report r = kind == 's' ? people.importStudents(in, actor) : kind == 'f' ? people.importStaff(in, actor) : people.importHeads(in, actor);
            r.counts.forEach((k, v) -> all.counts.merge(k, v, Integer::sum));
            r.errors.forEach(e -> all.err(file + ": " + e));
        }
    }
}
