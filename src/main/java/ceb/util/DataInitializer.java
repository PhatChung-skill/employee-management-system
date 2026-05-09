package ceb.util;

import ceb.model.Account;
import ceb.model.Employee;
import ceb.repository.AccountRepository;
import ceb.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataInitializer implements CommandLineRunner {
    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataInitializer(AccountRepository accountRepository, EmployeeRepository employeeRepository, BCryptPasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if(accountRepository.count() == 0) {
            Employee adminEmp = new Employee();
            adminEmp.setFullName("Administrator");
            adminEmp.setDob(LocalDate.of(1990,1,1));
            adminEmp.setGender("Male");
            adminEmp.setEmail("admin@company.com");
            employeeRepository.save(adminEmp);

            Account admin = new Account();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            admin.setEmp(adminEmp);
            accountRepository.save(admin);

            System.out.println("Default admin created: user=admin / pass=admin123");
        }
    }
}
