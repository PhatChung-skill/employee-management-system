package ceb.repository;

import ceb.model.Account;
import ceb.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findByUsername(String username);
    
    // Case-sensitive username lookup for login
    @Query(value = "SELECT * FROM Account WHERE CAST(username AS VARBINARY(MAX)) = CAST(:username AS VARBINARY(MAX))", nativeQuery = true)
    Optional<Account> findByUsernameCaseSensitive(@Param("username") String username);
    
    List<Account> findByRole(String role);
    Optional<Account> findByEmp(Employee emp);
    
    @Query("SELECT a FROM Account a WHERE " +
           "LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.role) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(a.emp IS NOT NULL AND (" +
           "LOWER(a.emp.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.emp.email) LIKE LOWER(CONCAT('%', :keyword, '%'))))")
    List<Account> searchAccounts(@Param("keyword") String keyword);
}
