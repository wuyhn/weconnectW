package com.weconnect.backend.repository;

import com.weconnect.backend.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    List<ChatRoomMember> findByRoomId(Long roomId);

    List<ChatRoomMember> findByUserId(Long userId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    java.util.Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
