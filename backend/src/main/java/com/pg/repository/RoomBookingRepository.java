package com.pg.repository;

import com.pg.entity.RoomBooking;
import com.pg.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomBookingRepository extends JpaRepository<RoomBooking, Long> {

    // a person's own requests, newest first (the caller passes a page size, so this can not grow without limit)
    @Query("SELECT b FROM RoomBooking b JOIN FETCH b.pg JOIN FETCH b.room JOIN FETCH b.user WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<RoomBooking> findMine(@Param("userId") Long userId, Pageable pageable);

    // owner list in pages: pending = true -> only waiting ones, false -> only answered ones
    @Query(value = "SELECT b FROM RoomBooking b JOIN FETCH b.pg JOIN FETCH b.room JOIN FETCH b.user WHERE b.pg.id = :pgId "
            + "AND ((:pending = true AND b.status = com.pg.entity.enums.BookingStatus.PENDING) "
            + "OR (:pending = false AND b.status <> com.pg.entity.enums.BookingStatus.PENDING)) ORDER BY b.createdAt DESC",
            countQuery = "SELECT COUNT(b) FROM RoomBooking b WHERE b.pg.id = :pgId "
            + "AND ((:pending = true AND b.status = com.pg.entity.enums.BookingStatus.PENDING) "
            + "OR (:pending = false AND b.status <> com.pg.entity.enums.BookingStatus.PENDING))")
    Page<RoomBooking> findForPg(@Param("pgId") Long pgId, @Param("pending") boolean pending, Pageable pageable);

    // holds on ONE room whose time ran out but that the scheduler has not released yet
    List<RoomBooking> findByRoomIdAndStatusAndExpiresAtBefore(Long roomId, BookingStatus status, LocalDateTime time);

    // for the public pages: how many unswept expired holds each room still carries
    @Query("SELECT b.room.id, COUNT(b) FROM RoomBooking b WHERE b.status = com.pg.entity.enums.BookingStatus.PENDING "
            + "AND b.expiresAt < :now AND b.room.id IN :roomIds GROUP BY b.room.id")
    List<Object[]> countExpiredHolds(@Param("roomIds") java.util.Collection<Long> roomIds, @Param("now") LocalDateTime now);

    boolean existsByUserIdAndStatus(Long userId, BookingStatus status);

    // waiting AND still inside their 48 hours (an expired one no longer counts as waiting)
    long countByPgIdAndStatusAndExpiresAtAfter(Long pgId, BookingStatus status, LocalDateTime now);

    // ids only (not entities): the caller locks and loads each one fresh, in its own transaction
    @Query("SELECT b.id FROM RoomBooking b WHERE b.status = :status AND b.expiresAt < :time ORDER BY b.id")
    List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") BookingStatus status, @Param("time") LocalDateTime time);

    // read just the ids of the user and the room, WITHOUT loading the booking, so we can lock
    // user and room first (lock order) and only then load the booking with fresh data
    @Query("SELECT b.user.id FROM RoomBooking b WHERE b.id = :id")
    Optional<Long> findUserId(@Param("id") Long id);

    @Query("SELECT b.room.id FROM RoomBooking b WHERE b.id = :id")
    Optional<Long> findRoomId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RoomBooking b JOIN FETCH b.pg JOIN FETCH b.room JOIN FETCH b.user WHERE b.id = :id")
    Optional<RoomBooking> findByIdForUpdate(@Param("id") Long id);
}
