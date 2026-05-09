package ceb.repository;

import ceb.model.Attendance;
import ceb.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {
    List<Attendance> findByEmp(Employee emp);
    List<Attendance> findByStatus(String status);
    List<Attendance> findByWorkDateBetween(LocalDate startDate, LocalDate endDate);
}
