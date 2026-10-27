package com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.model.Room;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findBySchoolCodeOrderByCapacityAsc(String schoolCode);
    List<Room> findAllByOrderByCapacityAsc();
    Optional<Room> findByRoomCodeIgnoreCase(String roomCode);
    Optional<Room> findBySchoolCodeAndRoomCodeIgnoreCase(String schoolCode, String roomCode);
}
