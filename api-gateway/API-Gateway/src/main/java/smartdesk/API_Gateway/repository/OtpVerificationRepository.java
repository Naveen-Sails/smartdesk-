package smartdesk.API_Gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import smartdesk.API_Gateway.entity.OtpVerification;
import smartdesk.API_Gateway.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    Optional<OtpVerification> findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(User user);
    Optional<OtpVerification> findTopByUserOrderByCreatedAtDesc(User user);
    List<OtpVerification> findAllByUserAndIsUsedFalse(User user);

    @Modifying
    @Query("UPDATE OtpVerification o SET o.isUsed = true WHERE o.user = :user AND o.isUsed = false")
    int invalidateAllUnusedOtpsForUser(@Param("user") User user);
}
