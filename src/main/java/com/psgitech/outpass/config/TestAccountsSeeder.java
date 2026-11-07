package com.psgitech.outpass.config;

import com.psgitech.outpass.domain.Department;
import com.psgitech.outpass.domain.Role;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.PeopleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo / testing accounts (one per kind of login), created once in their own "TEST" department so they
 * never mix with real classes. Turn off with SEED_TEST_ACCOUNTS=false for production.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class TestAccountsSeeder implements ApplicationRunner {
    private static final String D = "@psgitech.ac.in";
    private final OutpassProperties props;
    private final Repos.Users users;
    private final Repos.Departments departments;
    private final PeopleService people;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.isSeedTestAccounts() || users.existsByEmailIgnoreCase("teststudent" + D)) return;
        if (departments.findByCodeIgnoreCase("TEST").isEmpty()) {
            Department d = new Department();
            d.setCode("TEST");
            d.setName("Test Department (demo)");
            departments.save(d);
        }
        String a = "seed";
        people.saveStudent(new PeopleService.StudentData("Test Student", "TEST0001", "teststudent" + D, people.academicStartYear(), "TEST", "A", null, true), a);
        people.saveStaff(new PeopleService.StaffData("Test Tutor", "testtutor" + D, "9000000001", "TEST", "TUTOR", "1-A"), a);
        people.saveStaff(new PeopleService.StaffData("Test Tutor Two", "testtutor2" + D, "9000000002", "TEST", "TUTOR", "1-A"), a);
        people.saveStaff(new PeopleService.StaffData("Test Backup Tutor", "testbackup" + D, "9000000003", "TEST", "TUTOR", ""), a);
        people.saveStaff(new PeopleService.StaffData("Test HOD", "testhod" + D, "9000000004", "TEST", "HOD", ""), a);
        people.saveSimple("Test Head (Principal)", "testhead" + D, Role.HEAD, true, a);
        people.saveSimple("Test Security", "testsecurity" + D, Role.SECURITY, true, a);
        people.saveSimple("Test Admin", "testadmin" + D, Role.ADMIN, true, a);
        for (String n : new String[]{"teststudent", "testtutor", "testtutor2", "testbackup", "testhod"}) {
            users.findByEmailIgnoreCase(n + D).ifPresent(u -> { u.setLocalLogin(true); u.setMustChangePassword(true); });   // test accounts keep password login
        }
        log.warn("TEST ACCOUNTS created: teststudent/testtutor/testtutor2/testbackup/testhod/testhead/testsecurity/testadmin{}  "
                + "(student password: {} ; all others: {})", D, props.getStudentDefaultPassword(), props.getStaffDefaultPassword());
    }
}
