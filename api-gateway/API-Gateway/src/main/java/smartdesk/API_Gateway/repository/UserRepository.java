package smartdesk.API_Gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.role IN :roles ORDER BY u.createdAt DESC")
    List<User> findActiveStaffByRoles(@Param("roles") List<Role> roles);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.role IN :roles AND LOWER(u.department) = LOWER(:dept) ORDER BY u.createdAt DESC")
    List<User> findActiveStaffByRolesAndDepartment(@Param("roles") List<Role> roles, @Param("dept") String dept);
}
