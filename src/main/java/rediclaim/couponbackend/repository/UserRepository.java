package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
