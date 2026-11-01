package com.pg.service;

import com.pg.dto.request.ChargeRequest;
import com.pg.dto.response.ChargeResponse;
import com.pg.entity.PG;
import com.pg.entity.PgCharge;
import com.pg.exception.BusinessException;
import com.pg.exception.ResourceNotFoundException;
import com.pg.repository.PGRepository;
import com.pg.repository.PgChargeRepository;
import com.pg.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The owner's list of extra charges (everything except room rent). */
@Service
@RequiredArgsConstructor
public class ChargeService {

    static final int MAX_CHARGES_PER_PG = 20;

    private final PgChargeRepository chargeRepository;
    private final PGRepository pgRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ChargeResponse> list(String ownerEmail) {
        PG pg = ownerPg(ownerEmail);
        return chargeRepository.findByPgIdOrderByIdAsc(pg.getId()).stream().map(CostEstimateService::toResponse).toList();
    }

    @Transactional
    public ChargeResponse add(String ownerEmail, ChargeRequest req) {
        PG pg = ownerPg(ownerEmail);
        if (chargeRepository.countByPgId(pg.getId()) >= MAX_CHARGES_PER_PG) {
            throw new BusinessException("You can list at most " + MAX_CHARGES_PER_PG + " charges.");
        }
        PgCharge saved = chargeRepository.save(PgCharge.builder().pg(pg).name(req.getName().trim())
                .amount(req.getAmount()).kind(req.getKind()).note(blankToNull(req.getNote())).build());
        return CostEstimateService.toResponse(saved);
    }

    @Transactional
    public ChargeResponse update(String ownerEmail, Long id, ChargeRequest req) {
        PG pg = ownerPg(ownerEmail);
        PgCharge c = chargeRepository.findByIdAndPgId(id, pg.getId())   // scoped to THIS owner's PG
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
        c.setName(req.getName().trim());
        c.setAmount(req.getAmount());
        c.setKind(req.getKind());
        c.setNote(blankToNull(req.getNote()));
        return CostEstimateService.toResponse(chargeRepository.save(c));
    }

    @Transactional
    public void delete(String ownerEmail, Long id) {
        PG pg = ownerPg(ownerEmail);
        PgCharge c = chargeRepository.findByIdAndPgId(id, pg.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Charge not found"));
        chargeRepository.delete(c);
    }

    @Transactional(readOnly = true)
    public List<ChargeResponse> publicList(Long pgId) {
        return chargeRepository.findByPgIdOrderByIdAsc(pgId).stream().map(CostEstimateService::toResponse).toList();
    }

    private PG ownerPg(String ownerEmail) {
        var owner = userRepository.findByEmail(ownerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        return pgRepository.findByOwnerIdAndIsActiveTrue(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Set up your PG first."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
