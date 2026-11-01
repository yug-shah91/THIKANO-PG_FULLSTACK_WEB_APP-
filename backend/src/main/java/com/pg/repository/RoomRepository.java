package com.pg.repository;

import com.pg.entity.Room;
import com.pg.entity.enums.RoomStatus;
import com.pg.entity.enums.RoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    // Rooms of MANY PGs in ONE query (public search uses this instead of one query per PG)
    @Query("SELECT r FROM Room r WHERE r.pg.id IN :pgIds")
    List<Room> findByPgIds(@Param("pgIds") java.util.Collection<Long> pgIds);

    // All rooms in a PG — used by owner to see their rooms
    List<Room> findByPgId(Long pgId);

    // Available rooms in a PG — shown when allocating a new tenant
    List<Room> findByPgIdAndStatus(Long pgId, RoomStatus status);

    // Find room by number within a specific PG (room numbers unique per PG)
    Optional<Room> findByPgIdAndRoomNumber(Long pgId, String roomNumber);

    // Count available rooms in a PG — for dashboard stats
    long countByPgIdAndStatus(Long pgId, RoomStatus status);

    // Security check: verify a room belongs to the owner's PG
    // Used before any room operation to prevent cross-tenant access
    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE r.id = :roomId AND r.pg.owner.id = :ownerId")
    boolean existsByIdAndOwnerId(@Param("roomId") Long roomId, @Param("ownerId") Long ownerId);

    // Available rooms with capacity (for smart allocation)
    @Query("SELECT r FROM Room r WHERE r.pg.id = :pgId " +
           "AND r.status = :available " +
           "AND r.currentOccupancy < r.capacity " +
           "ORDER BY r.currentOccupancy DESC")
    List<Room> findRoomsWithStatusAndFreeBeds(@Param("pgId") Long pgId, @Param("available") RoomStatus available);

    default List<Room> findAvailableRoomsInPg(Long pgId) {
        return findRoomsWithStatusAndFreeBeds(pgId, RoomStatus.AVAILABLE);
    }

    // Loads the room and LOCKS its row until the transaction ends, so two requests
    // can not change the occupancy of the same room at the same time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);

    // Dashboard numbers computed from the REAL occupancy, not from the stored status,
    // so they stay correct even if a status was ever out of date.
    long countByPgId(Long pgId);

    @Query("SELECT COUNT(r) FROM Room r WHERE r.pg.id = :pgId AND r.status <> :maintenance " +
           "AND r.currentOccupancy < r.capacity")
    long countWithFreeBeds(@Param("pgId") Long pgId, @Param("maintenance") RoomStatus maintenance);

    @Query("SELECT COUNT(r) FROM Room r WHERE r.pg.id = :pgId AND r.status <> :maintenance " +
           "AND r.currentOccupancy >= r.capacity")
    long countFull(@Param("pgId") Long pgId, @Param("maintenance") RoomStatus maintenance);

    // beds in rooms that can be used (not under maintenance), and beds still free there
    @Query("SELECT COALESCE(SUM(r.capacity), 0) FROM Room r WHERE r.pg.id = :pgId AND r.status <> :maintenance")
    long sumUsableBeds(@Param("pgId") Long pgId, @Param("maintenance") RoomStatus maintenance);

    @Query("SELECT COALESCE(SUM(r.capacity - r.currentOccupancy), 0) FROM Room r WHERE r.pg.id = :pgId "
            + "AND r.status <> :maintenance AND r.currentOccupancy < r.capacity")
    long sumFreeBeds(@Param("pgId") Long pgId, @Param("maintenance") RoomStatus maintenance);

    // Rooms by type in a PG
    List<Room> findByPgIdAndType(Long pgId, RoomType type);
}
