package ceb.controller;

import ceb.model.*;
import ceb.repository.*;
import ceb.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public AdminController(AccountRepository accountRepository,
                          EmployeeRepository employeeRepository,
                          DepartmentRepository departmentRepository,
                          PositionRepository positionRepository,
                          AccountService accountService,
                          PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalAccounts = accountRepository.count();
        long totalEmployees = employeeRepository.count();
        long totalDepartments = departmentRepository.count();
        long totalPositions = positionRepository.count();
        long adminAccounts = accountRepository.findByRole("ADMIN").size();
        long hrAccounts = accountRepository.findByRole("HR").size();
        long employeeAccounts = accountRepository.findByRole("EMPLOYEE").size();
        
        model.addAttribute("totalAccounts", totalAccounts);
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("totalDepartments", totalDepartments);
        model.addAttribute("totalPositions", totalPositions);
        model.addAttribute("adminAccounts", adminAccounts);
        model.addAttribute("hrAccounts", hrAccounts);
        model.addAttribute("employeeAccounts", employeeAccounts);
        return "admin/dashboard";
    }

    // Account Management
    @GetMapping("/accounts")
    public String listAccounts(@RequestParam(required = false) String search, Model model) {
        List<Account> accounts;
        if (search != null && !search.trim().isEmpty()) {
            accounts = accountRepository.searchAccounts(search.trim());
            model.addAttribute("searchKeyword", search);
        } else {
            accounts = accountRepository.findAll();
        }
        model.addAttribute("accounts", accounts);
        return "admin/accounts/list";
    }

    @GetMapping("/accounts/add")
    public String showAddAccountForm(Model model) {
        model.addAttribute("account", new Account());
        model.addAttribute("employees", employeeRepository.findAll());
        return "admin/accounts/form";
    }

    @PostMapping("/accounts/add")
    public String addAccount(@ModelAttribute Account account,
                            @RequestParam(required = false) Integer empId,
                            @RequestParam String role,
                            @RequestParam String newPassword,
                            RedirectAttributes redirectAttributes) {
        try {
            if (accountRepository.findByUsernameCaseSensitive(account.getUsername()).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Tên đăng nhập đã tồn tại!");
                return "redirect:/admin/accounts/add";
            }
            
            // Check if employee already has an account
            if (empId != null && empId > 0) {
                Employee employee = employeeRepository.findById(empId)
                        .orElseThrow(() -> new RuntimeException("Employee not found"));
                
                if (accountRepository.findByEmp(employee).isPresent()) {
                    redirectAttributes.addFlashAttribute("error", "Nhân viên này đã có tài khoản");
                    return "redirect:/admin/accounts/add";
                }
                
                account.setEmp(employee);
            }
            
            // Set password from form parameter
            if (newPassword == null || newPassword.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Mật khẩu không được để trống!");
                return "redirect:/admin/accounts/add";
            }
            account.setPassword(newPassword);
            account.setRole(role);
            accountService.register(account);
            redirectAttributes.addFlashAttribute("success", "Thêm tài khoản thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm tài khoản: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @GetMapping("/accounts/edit/{id}")
    public String showEditAccountForm(@PathVariable Integer id, Model model) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        model.addAttribute("account", account);
        model.addAttribute("employees", employeeRepository.findAll());
        return "admin/accounts/form";
    }

    @PostMapping("/accounts/edit/{id}")
    public String updateAccount(@PathVariable Integer id,
                               @ModelAttribute Account account,
                               @RequestParam(required = false) Integer empId,
                               @RequestParam String role,
                               @RequestParam(required = false) String newPassword,
                               RedirectAttributes redirectAttributes) {
        try {
            Account existingAccount = accountRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Account not found"));
            
            // Check username uniqueness (excluding current account)
            if (!existingAccount.getUsername().equals(account.getUsername())) {
                if (accountRepository.findByUsernameCaseSensitive(account.getUsername()).isPresent()) {
                    redirectAttributes.addFlashAttribute("error", "Tên đăng nhập đã tồn tại!");
                    return "redirect:/admin/accounts/edit/" + id;
                }
            }
            
            existingAccount.setUsername(account.getUsername());
            existingAccount.setRole(role);
            
            // Update password if provided
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                existingAccount.setPassword(passwordEncoder.encode(newPassword));
            }
            
            // Update employee association
            if (empId != null && empId > 0) {
                Employee employee = employeeRepository.findById(empId)
                        .orElseThrow(() -> new RuntimeException("Employee not found"));
                existingAccount.setEmp(employee);
            } else {
                existingAccount.setEmp(null);
            }
            
            accountRepository.save(existingAccount);
            redirectAttributes.addFlashAttribute("success", "Cập nhật tài khoản thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật tài khoản: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/accounts/delete/{id}")
    @Transactional
    public String deleteAccount(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            accountRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa tài khoản thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa tài khoản: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    // Backup Accounts
    @GetMapping("/accounts/backup")
    public ResponseEntity<byte[]> backupAccounts() {
        try {
            List<Account> accounts = accountRepository.findAll();
            
            // Create backup data structure
            AccountBackup backup = new AccountBackup();
            backup.setBackupDate(LocalDateTime.now().toString());
            backup.setAccounts(accounts);
            
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(backup);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            
            String filename = "accounts_backup_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", filename);
            
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Restore Accounts
    @PostMapping("/accounts/restore")
    public String restoreAccounts(@RequestParam("file") MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "File không được để trống!");
                return "redirect:/admin/accounts";
            }
            
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            AccountBackup backup = objectMapper.readValue(content, AccountBackup.class);
            
            if (backup.getAccounts() != null) {
                for (Account account : backup.getAccounts()) {
                    // Check if account exists
                    accountRepository.findByUsernameCaseSensitive(account.getUsername()).ifPresent(existing -> {
                        accountRepository.delete(existing);
                    });
                    // Save account (password should already be encoded in backup)
                    accountRepository.save(account);
                }
            }
            
            redirectAttributes.addFlashAttribute("success", 
                "Khôi phục " + (backup.getAccounts() != null ? backup.getAccounts().size() : 0) + " tài khoản thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi khôi phục: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    // Department Management
    @GetMapping("/departments")
    public String listDepartments(Model model) {
        List<Department> departments = departmentRepository.findAll();
        model.addAttribute("departments", departments);
        return "admin/departments/list";
    }

    @GetMapping("/departments/add")
    public String showAddDepartmentForm(Model model) {
        model.addAttribute("department", new Department());
        return "admin/departments/form";
    }

    @PostMapping("/departments/add")
    public String addDepartment(@ModelAttribute Department department, RedirectAttributes redirectAttributes) {
        try {
            departmentRepository.save(department);
            redirectAttributes.addFlashAttribute("success", "Thêm phòng ban thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm phòng ban: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @GetMapping("/departments/edit/{id}")
    public String showEditDepartmentForm(@PathVariable Integer id, Model model) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        model.addAttribute("department", department);
        return "admin/departments/form";
    }

    @PostMapping("/departments/edit/{id}")
    public String updateDepartment(@PathVariable Integer id,
                                  @ModelAttribute Department department,
                                  RedirectAttributes redirectAttributes) {
        try {
            department.setDeptId(id);
            departmentRepository.save(department);
            redirectAttributes.addFlashAttribute("success", "Cập nhật phòng ban thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật phòng ban: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/delete/{id}")
    @Transactional
    public String deleteDepartment(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            Department department = departmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            
            // Set department to null for all employees that reference this department
            List<Employee> employees = employeeRepository.findAll();
            for (Employee employee : employees) {
                if (employee.getDepartment() != null && employee.getDepartment().getDeptId().equals(id)) {
                    employee.setDepartment(null);
                    employeeRepository.save(employee);
                }
            }
            
            departmentRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa phòng ban thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa phòng ban: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    // Position Management
    @GetMapping("/positions")
    public String listPositions(Model model) {
        List<Position> positions = positionRepository.findAll();
        model.addAttribute("positions", positions);
        return "admin/positions/list";
    }

    @GetMapping("/positions/add")
    public String showAddPositionForm(Model model) {
        model.addAttribute("position", new Position());
        return "admin/positions/form";
    }

    @PostMapping("/positions/add")
    public String addPosition(@ModelAttribute Position position, RedirectAttributes redirectAttributes) {
        try {
            positionRepository.save(position);
            redirectAttributes.addFlashAttribute("success", "Thêm chức vụ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm chức vụ: " + e.getMessage());
        }
        return "redirect:/admin/positions";
    }

    @GetMapping("/positions/edit/{id}")
    public String showEditPositionForm(@PathVariable Integer id, Model model) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Position not found"));
        model.addAttribute("position", position);
        return "admin/positions/form";
    }

    @PostMapping("/positions/edit/{id}")
    public String updatePosition(@PathVariable Integer id,
                                @ModelAttribute Position position,
                                RedirectAttributes redirectAttributes) {
        try {
            position.setPositionId(id);
            positionRepository.save(position);
            redirectAttributes.addFlashAttribute("success", "Cập nhật chức vụ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật chức vụ: " + e.getMessage());
        }
        return "redirect:/admin/positions";
    }

    @PostMapping("/positions/delete/{id}")
    @Transactional
    public String deletePosition(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            Position position = positionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Position not found"));
            
            // Set position to null for all employees that reference this position
            List<Employee> employees = employeeRepository.findAll();
            for (Employee employee : employees) {
                if (employee.getPosition() != null && employee.getPosition().getPositionId().equals(id)) {
                    employee.setPosition(null);
                    employeeRepository.save(employee);
                }
            }
            
            positionRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa chức vụ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa chức vụ: " + e.getMessage());
        }
        return "redirect:/admin/positions";
    }

    // Inner class for backup structure
    public static class AccountBackup {
        private String backupDate;
        private List<Account> accounts;

        public String getBackupDate() {
            return backupDate;
        }

        public void setBackupDate(String backupDate) {
            this.backupDate = backupDate;
        }

        public List<Account> getAccounts() {
            return accounts;
        }

        public void setAccounts(List<Account> accounts) {
            this.accounts = accounts;
        }
    }
}
