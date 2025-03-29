package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.Admin;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
}
