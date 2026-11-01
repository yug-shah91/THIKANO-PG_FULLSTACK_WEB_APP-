package com.pg.service;

import com.pg.dto.response.ChargeResponse;
import com.pg.dto.response.CostEstimateResponse;
import com.pg.entity.PG;
import com.pg.entity.PgCharge;
import com.pg.entity.Room;
import com.pg.entity.enums.ChargeKind;
import com.pg.entity.enums.RoomStatus;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.PGRepository;
import com.pg.repository.PgChargeRepository;
import com.pg.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Adds up what a person would REALLY pay per month: room rent + the charges the owner
 * disclosed + the optional services they choose + their own commute estimate.
 * It only uses numbers the owner entered. Nothing is guessed here.
 */
@Service
@RequiredArgsConstructor
public class CostEstimateService {

    static final BigDecimal MAX_DAILY_COMMUTE = new BigDecimal("5000");

    private final PGRepository pgRepository;
    private final RoomRepository roomRepository;
    private final PgChargeRepository chargeRepository;

    @Transactional(readOnly = true)
    public CostEstimateResponse estimate(Long pgId, Long roomId, Set<Long> optionalIds,
                                         BigDecimal commutePerDay, Integer commuteDays) {
        PG pg = pgRepository.findById(pgId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("PG not found"));

        Room room = roomRepository.findById(roomId)
                .filter(r -> r.getPg().getId().equals(pgId))
                .orElseThrow(() -> new ResourceNotFoundException("Room not found in this PG"));
        if (room.getStatus() == RoomStatus.UNDER_MAINTENANCE) {
            throw new BusinessException("This room is not available.");
        }
        BigDecimal rent = room.getMonthlyRent() != null ? room.getMonthlyRent() : pg.getBaseMonthlyRent();
        if (rent == null) {
            throw new BusinessException("The owner has not set a rent for this room yet.");
        }

        BigDecimal commute = commute(commutePerDay, commuteDays);

        List<PgCharge> all = chargeRepository.findByPgIdOrderByIdAsc(pgId);
        Set<Long> wanted = optionalIds == null ? Set.of() : optionalIds;
        // an id that is not an optional charge of THIS PG is an error, not silently ignored
        for (Long id : wanted) {
            boolean ok = all.stream().anyMatch(c -> c.getId().equals(id) && c.getKind() == ChargeKind.OPTIONAL_MONTHLY);
            if (!ok) throw new BusinessException("One of the chosen services does not exist at this PG.");
        }

        List<PgCharge> required = all.stream().filter(c -> c.getKind() == ChargeKind.REQUIRED_MONTHLY).toList();
        List<PgCharge> chosen = all.stream()
                .filter(c -> c.getKind() == ChargeKind.OPTIONAL_MONTHLY && wanted.contains(c.getId())).toList();
        List<PgCharge> oneTime = all.stream().filter(c -> c.getKind() == ChargeKind.ONE_TIME).toList();

        BigDecimal monthly = rent.add(sum(required)).add(sum(chosen)).add(commute);
        BigDecimal oneTimeTotal = sum(oneTime);

        return CostEstimateResponse.builder()
                .pgName(pg.getName())
                .roomNumber(room.getRoomNumber())
                .rent(rent)
                .requiredCharges(required.stream().map(CostEstimateService::toResponse).toList())
                .chosenOptional(chosen.stream().map(CostEstimateService::toResponse).toList())
                .commute(commute)
                .monthlyTotal(monthly)
                .advertisedRent(rent)
                .extraOverRent(monthly.subtract(rent))
                .oneTimeCharges(oneTime.stream().map(CostEstimateService::toResponse).toList())
                .oneTimeTotal(oneTimeTotal)
                .firstMonthTotal(monthly.add(oneTimeTotal))
                .build();
    }

    static BigDecimal commute(BigDecimal perDay, Integer days) {
        if (perDay == null || days == null) return BigDecimal.ZERO;
        if (perDay.signum() < 0 || perDay.compareTo(MAX_DAILY_COMMUTE) > 0) {
            throw new BusinessException("Daily commute cost must be between 0 and " + MAX_DAILY_COMMUTE + ".");
        }
        if (days < 0 || days > 31) {
            throw new BusinessException("Commute days per month must be between 0 and 31.");
        }
        return perDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<PgCharge> charges) {
        return charges.stream().map(PgCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static ChargeResponse toResponse(PgCharge c) {
        return ChargeResponse.builder().id(c.getId()).name(c.getName()).amount(c.getAmount())
                .kind(c.getKind()).note(c.getNote()).build();
    }
}
