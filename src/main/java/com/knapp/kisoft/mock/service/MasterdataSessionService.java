package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.persistence.MasterdataSessionDeltaEntity;
import com.knapp.kisoft.mock.persistence.MasterdataSessionDeltaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterdataSessionService {

    public static final String DOMAIN_PACK_UNIT = "PACK_UNIT";
    public static final String DOMAIN_ROUTE = "ROUTE";

    private final MasterdataSessionDeltaRepository deltaRepository;

    public MasterdataSessionService(MasterdataSessionDeltaRepository deltaRepository) {
        this.deltaRepository = deltaRepository;
    }

    @Transactional
    public void startSession(String domain, String clientNumber) {
        deltaRepository.deleteByDomainAndClientNumber(domain, clientNumber);
    }

    @Transactional
    public void markSeen(String domain, String clientNumber, String keyValue) {
        if (!deltaRepository.existsByDomainAndClientNumberAndKeyValue(domain, clientNumber, keyValue)) {
            deltaRepository.save(new MasterdataSessionDeltaEntity(domain, clientNumber, keyValue));
        }
    }

    @Transactional(readOnly = true)
    public boolean wasSeen(String domain, String clientNumber, String keyValue) {
        return deltaRepository.existsByDomainAndClientNumberAndKeyValue(domain, clientNumber, keyValue);
    }

    @Transactional
    public void clearSession(String domain, String clientNumber) {
        deltaRepository.deleteByDomainAndClientNumber(domain, clientNumber);
    }
}

