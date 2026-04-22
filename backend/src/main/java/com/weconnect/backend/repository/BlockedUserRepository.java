package com.weconnect.backend.repository;

import com.weconnect.backend.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    Optional<BlockedUser> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<BlockedUser> findByBlockerId(Long blockerId);

    List<BlockedUser> findByBlockedId(Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // Tất cả blocked records liên quan đến user (dùng cho cascade delete)
    List<BlockedUser> findByBlockerIdOrBlockedId(Long blockerId, Long blockedId);
}
