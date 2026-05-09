package ceb.controller;

import ceb.model.Account;
import ceb.model.Employee;
import ceb.repository.AccountRepository;
import ceb.repository.AttendanceRepository;
import ceb.repository.ContractRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmployeeController {

    private final AccountRepository accountRepository;
    private final AttendanceRepository attendanceRepository;
    private final ContractRepository contractRepository;

    public EmployeeController(AccountRepository accountRepository,
                              AttendanceRepository attendanceRepository,
                              ContractRepository contractRepository) {
        this.accountRepository = accountRepository;
        this.attendanceRepository = attendanceRepository;
        this.contractRepository = contractRepository;
    }

    private Employee getCurrentEmployee(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String username = authentication.getName();
        Account account = accountRepository.findByUsernameCaseSensitive(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return account.getEmp();
    }

    @GetMapping("/employee/home")
    public String employeeHome(Authentication authentication, Model model) {
        Employee employee = getCurrentEmployee(authentication);
        if (employee == null) {
            return "redirect:/login";
        }
        model.addAttribute("employee", employee);
        return "employee/home";
    }

    @GetMapping("/employee/personal-info")
    public String personalInfo(Authentication authentication, Model model) {
        Employee employee = getCurrentEmployee(authentication);
        if (employee == null) {
            return "redirect:/login";
        }
        model.addAttribute("employee", employee);
        return "employee/personal-info";
    }

    @GetMapping("/employee/timesheet")
    public String timesheet(Authentication authentication, Model model) {
        Employee employee = getCurrentEmployee(authentication);
        if (employee == null) {
            return "redirect:/login";
        }
        model.addAttribute("employee", employee);
        model.addAttribute("attendances", attendanceRepository.findByEmp(employee));
        return "employee/timesheet";
    }

    @GetMapping("/employee/contracts")
    public String contracts(Authentication authentication, Model model) {
        Employee employee = getCurrentEmployee(authentication);
        if (employee == null) {
            return "redirect:/login";
        }
        model.addAttribute("employee", employee);
        model.addAttribute("contracts", contractRepository.findByEmp(employee));
        return "employee/contracts";
    }
}
