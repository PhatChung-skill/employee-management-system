package ceb.controller;

import ceb.model.Account;
import ceb.model.Employee;
import ceb.repository.AccountRepository;
import ceb.repository.EmployeeRepository;
import ceb.service.AccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {
    private final AccountService accountService;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;

    public AuthController(AccountService accountService, EmployeeRepository employeeRepository, AccountRepository accountRepository) {
        this.accountService = accountService;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/login")
    public String login(Model model, String error, String logout, String registered) {
        if (error != null) {
            model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng");
        }
        if (logout != null) {
            model.addAttribute("message", "Bạn đã đăng xuất thành công");
        }
        if (registered != null) {
            model.addAttribute("message", "Đăng ký thành công! Vui lòng đăng nhập.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("account", new Account());
        model.addAttribute("employee", new Employee());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("account") Account account,
                           @ModelAttribute("employee") Employee employee,
                           Model model) {
        if(accountRepository.findByUsernameCaseSensitive(account.getUsername()).isPresent()){
            model.addAttribute("error","Username đã tồn tại");
            return "register";
        }
        employeeRepository.save(employee);
        account.setEmp(employee);
        account.setRole("EMPLOYEE");
        accountService.register(account);
        return "redirect:/login?registered";
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
