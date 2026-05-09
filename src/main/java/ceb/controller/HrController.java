package ceb.controller;

import ceb.model.*;
import ceb.repository.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/hr")
public class HrController {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final AttendanceRepository attendanceRepository;
    private final ContractRepository contractRepository;
    private final AccountRepository accountRepository;

    public HrController(EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository,
                        PositionRepository positionRepository,
                        AttendanceRepository attendanceRepository,
                        ContractRepository contractRepository,
                        AccountRepository accountRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.attendanceRepository = attendanceRepository;
        this.contractRepository = contractRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalEmployees = employeeRepository.count();
        long totalAttendances = attendanceRepository.count();
        long totalContracts = contractRepository.count();
        long pendingAttendances = attendanceRepository.findByStatus("Chờ duyệt").size();

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("totalAttendances", totalAttendances);
        model.addAttribute("totalContracts", totalContracts);
        model.addAttribute("pendingAttendances", pendingAttendances);
        return "hr/dashboard";
    }

    // Employee Management
    @GetMapping("/employees")
    public String listEmployees(@RequestParam(required = false) String search, Model model) {
        List<Employee> employees;
        if (search != null && !search.trim().isEmpty()) {
            employees = employeeRepository.searchEmployees(search.trim());
            model.addAttribute("searchKeyword", search);
        } else {
            employees = employeeRepository.findAll();
        }
        model.addAttribute("employees", employees);
        return "hr/employees/list";
    }

    @GetMapping("/employees/add")
    public String showAddEmployeeForm(Model model) {
        model.addAttribute("employee", new Employee());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("positions", positionRepository.findAll());
        return "hr/employees/form";
    }

    @PostMapping("/employees/add")
    public String addEmployee(@ModelAttribute Employee employee,
                              @RequestParam(required = false) Integer deptId,
                              @RequestParam(required = false) Integer positionId,
                              RedirectAttributes redirectAttributes) {
        try {
            if (deptId != null) {
                Department department = departmentRepository.findById(deptId).orElse(null);
                employee.setDepartment(department);
            }
            if (positionId != null) {
                Position position = positionRepository.findById(positionId).orElse(null);
                employee.setPosition(position);
            }
            employeeRepository.save(employee);
            redirectAttributes.addFlashAttribute("success", "Thêm nhân viên thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm nhân viên: " + e.getMessage());
        }
        return "redirect:/hr/employees";
    }

    @GetMapping("/employees/view/{id}")
    public String viewEmployeeDetails(@PathVariable Integer id, Model model) {
        try {
            Employee employee = employeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
            model.addAttribute("employee", employee);
            model.addAttribute("contracts", contractRepository.findByEmp(employee));
            
            // Get attendance records, sort by workDate descending, and limit to 10 most recent
            List<Attendance> allAttendances = attendanceRepository.findByEmp(employee);
            List<Attendance> recentAttendances = allAttendances.stream()
                    .sorted((a1, a2) -> {
                        if (a1.getWorkDate() == null && a2.getWorkDate() == null) return 0;
                        if (a1.getWorkDate() == null) return 1;
                        if (a2.getWorkDate() == null) return -1;
                        return a2.getWorkDate().compareTo(a1.getWorkDate());
                    })
                    .limit(10)
                    .toList();
            model.addAttribute("attendances", recentAttendances);
            model.addAttribute("totalAttendances", allAttendances.size());
            
            // Check if employee has an account
            accountRepository.findByEmp(employee).ifPresent(account -> {
                model.addAttribute("hasAccount", true);
                model.addAttribute("account", account);
            });
            return "hr/employees/details";
        } catch (Exception e) {
            model.addAttribute("error", "Không tìm thấy nhân viên: " + e.getMessage());
            return "redirect:/hr/employees";
        }
    }

    @GetMapping("/employees/edit/{id}")
    public String showEditEmployeeForm(@PathVariable Integer id, Model model) {
        try {
            Employee employee = employeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
            model.addAttribute("employee", employee);
            model.addAttribute("departments", departmentRepository.findAll());
            model.addAttribute("positions", positionRepository.findAll());
            return "hr/employees/form";
        } catch (Exception e) {
            model.addAttribute("error", "Không tìm thấy nhân viên: " + e.getMessage());
            return "redirect:/hr/employees";
        }
    }

    @PostMapping("/employees/edit/{id}")
    public String updateEmployee(@PathVariable Integer id,
                                 @ModelAttribute Employee employee,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dob,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate hireDate,
                                 @RequestParam(required = false) Integer deptId,
                                 @RequestParam(required = false) Integer positionId,
                                 RedirectAttributes redirectAttributes) {
        try {
            Employee existingEmployee = employeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

            // Update all fields
            if (employee.getFullName() != null) {
                existingEmployee.setFullName(employee.getFullName());
            }
            existingEmployee.setGender(employee.getGender());
            // Update dates - use request parameters which handle empty strings as null
            // This allows both updating and clearing dates
            existingEmployee.setDob(dob);
            existingEmployee.setPhone(employee.getPhone());
            existingEmployee.setEmail(employee.getEmail());
            existingEmployee.setAddress(employee.getAddress());
            existingEmployee.setHireDate(hireDate);

            // Update department
            if (deptId != null && deptId > 0) {
                Department department = departmentRepository.findById(deptId)
                        .orElseThrow(() -> new RuntimeException("Department not found with id: " + deptId));
                existingEmployee.setDepartment(department);
            } else {
                existingEmployee.setDepartment(null);
            }

            // Update position
            if (positionId != null && positionId > 0) {
                Position position = positionRepository.findById(positionId)
                        .orElseThrow(() -> new RuntimeException("Position not found with id: " + positionId));
                existingEmployee.setPosition(position);
            } else {
                existingEmployee.setPosition(null);
            }

            employeeRepository.save(existingEmployee);
            redirectAttributes.addFlashAttribute("success", "Cập nhật nhân viên thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật nhân viên: " + e.getMessage());
            return "redirect:/hr/employees/edit/" + id;
        }
        return "redirect:/hr/employees";
    }

    @PostMapping("/employees/delete/{id}")
    @Transactional
    public String deleteEmployee(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            // Delete associated Account first (if exists)
            accountRepository.findByEmp(employee).ifPresent(account -> {
                accountRepository.delete(account);
            });

            // Delete associated Attendance records
            List<Attendance> attendances = attendanceRepository.findByEmp(employee);
            if (!attendances.isEmpty()) {
                attendanceRepository.deleteAll(attendances);
            }

            // Delete associated Contract records
            List<Contract> contracts = contractRepository.findByEmp(employee);
            if (!contracts.isEmpty()) {
                contractRepository.deleteAll(contracts);
            }

            // Finally, delete the Employee
            employeeRepository.delete(employee);

            redirectAttributes.addFlashAttribute("success", "Xóa nhân viên và tài khoản liên quan thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa nhân viên: " + e.getMessage());
        }
        return "redirect:/hr/employees";
    }

    // Attendance Management
    @GetMapping("/attendance")
    public String listAttendance(@RequestParam(required = false) String status, Model model) {
        List<Attendance> attendances;
        if (status != null && !status.trim().isEmpty()) {
            attendances = attendanceRepository.findByStatus(status);
        } else {
            attendances = attendanceRepository.findAll();
        }
        model.addAttribute("attendances", attendances);
        model.addAttribute("selectedStatus", status);
        return "hr/attendance/list";
    }

    @GetMapping("/attendance/edit/{id}")
    public String showEditAttendanceForm(@PathVariable Integer id, Model model) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attendance not found"));
        model.addAttribute("attendance", attendance);
        return "hr/attendance/form";
    }

    @PostMapping("/attendance/edit/{id}")
    public String updateAttendance(@PathVariable Integer id,
                                   @ModelAttribute Attendance attendance,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate workDate,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime checkIn,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime checkOut,
                                   @RequestParam(required = false) String status,
                                   RedirectAttributes redirectAttributes) {
        try {
            Attendance existingAttendance = attendanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Attendance not found"));
            if (workDate != null) existingAttendance.setWorkDate(workDate);
            
            // Update checkIn and checkOut with request values
            // HTML time inputs send their current values; empty inputs send null
            // This allows both updating and clearing the times
            existingAttendance.setCheckIn(checkIn);
            existingAttendance.setCheckOut(checkOut);
            
            // If both checkIn and checkOut are null/empty, automatically set status to "Vắng mặt" (Absent)
            // unless user explicitly provided a different status
            if (checkIn == null && checkOut == null) {
                // Both times are null, set to absent unless user specified otherwise
                if (status == null || status.isEmpty()) {
                    existingAttendance.setStatus("Vắng mặt");
                } else {
                    existingAttendance.setStatus(status);
                }
            } else if (status != null && !status.isEmpty()) {
                // User provided a status, use it
                existingAttendance.setStatus(status);
            }
            // If times are present but no status provided, keep existing status
            
            attendanceRepository.save(existingAttendance);
            redirectAttributes.addFlashAttribute("success", "Cập nhật chấm công thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật chấm công: " + e.getMessage());
        }
        return "redirect:/hr/attendance";
    }

    @PostMapping("/attendance/approve/{id}")
    public String approveAttendance(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            Attendance attendance = attendanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Attendance not found"));
            attendance.setStatus("Đã duyệt");
            attendanceRepository.save(attendance);
            redirectAttributes.addFlashAttribute("success", "Duyệt chấm công thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi duyệt chấm công: " + e.getMessage());
        }
        return "redirect:/hr/attendance";
    }

    @PostMapping("/attendance/delete/{id}")
    public String deleteAttendance(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            attendanceRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa chấm công thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa chấm công: " + e.getMessage());
        }
        return "redirect:/hr/attendance";
    }

    // Contract Management
    @GetMapping("/contracts")
    public String listContracts(@RequestParam(required = false) String status, Model model) {
        List<Contract> contracts;
        if (status != null && !status.trim().isEmpty()) {
            contracts = contractRepository.findByStatus(status);
        } else {
            contracts = contractRepository.findAll();
        }
        model.addAttribute("contracts", contracts);
        model.addAttribute("selectedStatus", status);
        return "hr/contracts/list";
    }

    @GetMapping("/contracts/renew/{id}")
    public String showRenewContractForm(@PathVariable Integer id, Model model) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        model.addAttribute("contract", contract);
        return "hr/contracts/renew";
    }

    @PostMapping("/contracts/renew/{id}")
    public String renewContract(@PathVariable Integer id,
                                @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate newEndDate,
                                @RequestParam(required = false) BigDecimal newSalary,
                                RedirectAttributes redirectAttributes) {
        try {
            Contract contract = contractRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));
            contract.setEndDate(newEndDate);
            if (newSalary != null) {
                contract.setSalary(newSalary);
            }
            contract.setStatus("Đang hiệu lực");
            contractRepository.save(contract);
            redirectAttributes.addFlashAttribute("success", "Gia hạn hợp đồng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi gia hạn hợp đồng: " + e.getMessage());
        }
        return "redirect:/hr/contracts";
    }

    @PostMapping("/contracts/terminate/{id}")
    public String terminateContract(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            Contract contract = contractRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));
            contract.setStatus("Đã chấm dứt");
            contract.setEndDate(LocalDate.now());
            contractRepository.save(contract);
            redirectAttributes.addFlashAttribute("success", "Chấm dứt hợp đồng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi chấm dứt hợp đồng: " + e.getMessage());
        }
        return "redirect:/hr/contracts";
    }

    @GetMapping("/contracts/add")
    public String showAddContractForm(Model model) {
        model.addAttribute("contract", new Contract());
        model.addAttribute("employees", employeeRepository.findAll());
        return "hr/contracts/form";
    }

    @PostMapping("/contracts/add")
    public String addContract(@ModelAttribute Contract contract,
                              @RequestParam Integer empId,
                              RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new RuntimeException("Employee not found with id: " + empId));

            contract.setEmp(employee);
            if (contract.getStatus() == null || contract.getStatus().isEmpty()) {
                contract.setStatus("Đang hiệu lực");
            }

            // Validate required fields
            if (contract.getContractType() == null || contract.getContractType().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Loại hợp đồng không được để trống!");
                return "redirect:/hr/contracts/add";
            }
            if (contract.getStartDate() == null) {
                redirectAttributes.addFlashAttribute("error", "Ngày bắt đầu không được để trống!");
                return "redirect:/hr/contracts/add";
            }
            if (contract.getEndDate() == null) {
                redirectAttributes.addFlashAttribute("error", "Ngày kết thúc không được để trống!");
                return "redirect:/hr/contracts/add";
            }
            if (contract.getStartDate().isAfter(contract.getEndDate())) {
                redirectAttributes.addFlashAttribute("error", "Ngày bắt đầu không được sau ngày kết thúc!");
                return "redirect:/hr/contracts/add";
            }

            contractRepository.save(contract);
            redirectAttributes.addFlashAttribute("success", "Thêm hợp đồng thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm hợp đồng: " + e.getMessage());
            return "redirect:/hr/contracts/add";
        }
        return "redirect:/hr/contracts";
    }

    @GetMapping("/contracts/edit/{id}")
    public String showEditContractForm(@PathVariable Integer id, Model model) {
        try {
            Contract contract = contractRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));
            model.addAttribute("contract", contract);
            model.addAttribute("employees", employeeRepository.findAll());
            return "hr/contracts/edit";
        } catch (Exception e) {
            return "redirect:/hr/contracts";
        }
    }

    @PostMapping("/contracts/edit/{id}")
    public String updateContract(@PathVariable Integer id,
                                 @ModelAttribute Contract contract,
                                 @RequestParam Integer empId,
                                 @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                 @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                                 RedirectAttributes redirectAttributes) {
        try {
            Contract existingContract = contractRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));

            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            existingContract.setEmp(employee);
            existingContract.setContractType(contract.getContractType());
            existingContract.setStartDate(startDate);
            existingContract.setEndDate(endDate);
            existingContract.setSalary(contract.getSalary());
            existingContract.setStatus(contract.getStatus());

            contractRepository.save(existingContract);
            redirectAttributes.addFlashAttribute("success", "Cập nhật hợp đồng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật hợp đồng: " + e.getMessage());
        }
        return "redirect:/hr/contracts";
    }

    @PostMapping("/contracts/delete/{id}")
    @Transactional
    public String deleteContract(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            contractRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa hợp đồng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa hợp đồng: " + e.getMessage());
        }
        return "redirect:/hr/contracts";
    }

    @GetMapping("/attendance/add")
    public String showAddAttendanceForm(Model model) {
        model.addAttribute("attendance", new Attendance());
        model.addAttribute("employees", employeeRepository.findAll());
        return "hr/attendance/add";
    }

    @PostMapping("/attendance/add")
    public String addAttendance(@ModelAttribute Attendance attendance,
                                @RequestParam Integer empId,
                                @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate workDate,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime checkIn,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime checkOut,
                                @RequestParam(required = false) String status,
                                RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            attendance.setEmp(employee);
            attendance.setWorkDate(workDate);
            attendance.setCheckIn(checkIn);
            attendance.setCheckOut(checkOut);
            
            // If both checkIn and checkOut are null/empty, set status to "Vắng mặt" (Absent)
            if (checkIn == null && checkOut == null) {
                attendance.setStatus("Vắng mặt");
            } else if (status != null && !status.isEmpty()) {
                attendance.setStatus(status);
            } else {
                attendance.setStatus("Chờ duyệt");
            }

            attendanceRepository.save(attendance);
            redirectAttributes.addFlashAttribute("success", "Thêm chấm công thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm chấm công: " + e.getMessage());
        }
        return "redirect:/hr/attendance";
    }
}
