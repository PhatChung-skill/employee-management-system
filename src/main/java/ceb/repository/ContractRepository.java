package ceb.repository;

import ceb.model.Contract;
import ceb.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Integer> {
    List<Contract> findByEmp(Employee emp);
    List<Contract> findByStatus(String status);
}
