package com.psgitech.outpass.config;

import com.psgitech.outpass.domain.Department;
import com.psgitech.outpass.domain.Role;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** First-run bootstrap: default administrator, departments and a main gate. */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {
    private final Repos.Users users;
    private final Repos.Departments departments;
    private final PasswordEncoder encoder;
    private final OutpassProperties props;
    private final AdminService admin;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.countByRole(Role.ADMIN) == 0) {
            String pw = props.getAdminPassword();
            boolean generated = pw == null || pw.isBlank();
            if (generated) pw = props.getStaffDefaultPassword();      // same default as every non-student account; must be changed at first login
            UserAccount u = new UserAccount();
            u.setFullName(props.getAdminName());
            u.setEmail(props.getAdminEmail().toLowerCase());
            u.setRole(Role.ADMIN);
            u.setPasswordHash(encoder.encode(pw));
            u.setLocalLogin(true);                 // break-glass administrator keeps password sign-in
            u.setMustChangePassword(generated);
            users.save(u);
            log.warn("==========================================================");
            log.warn(" Administrator account created: {}", u.getEmail());
            if (generated) log.warn(" Initial password: {}  (change it at first login; or set ADMIN_PASSWORD)", pw);
            log.warn("==========================================================");
        }
        if (departments.count() == 0) {
            List<String[]> seed = List.of(
                    new String[]{"CSE", "Computer Science and Engineering"},
                    new String[]{"IT", "Information Technology"},
                    new String[]{"ECE", "Electronics and Communication Engineering"},
                    new String[]{"EEE", "Electrical and Electronics Engineering"},
                    new String[]{"MECH", "Mechanical Engineering"},
                    new String[]{"CIVIL", "Civil Engineering"},
                    new String[]{"AIDS", "Artificial Intelligence and Data Science"});
            for (String[] s : seed) {
                Department d = new Department();
                d.setCode(s[0]);
                d.setName(s[1]);
                departments.save(d);
            }
        }
        admin.ensureGate("Main Gate");
        admin.ensureGate("2nd Gate");
    }
}
