package com.verifiedai.mistake.passport.controller;

import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.TrapLifecycleStatus;
import com.verifiedai.mistake.passport.service.MistakePassportReadModelService;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = {"/v1/mistake-passport", "/api/v1/mistake-passport"})
public class MistakePassportQueryController {

    private final MistakePassportReadModelService readModelService;

    public MistakePassportQueryController(MistakePassportReadModelService readModelService) {
        this.readModelService = readModelService;
    }

    @GetMapping
    public ResponseEntity<MistakePassportResponseDto> getMyMistakePassport(
            @AuthenticationPrincipal AuthenticatedLearnerPrincipal principal,
            @RequestParam(name = "status", required = false) TrapLifecycleStatus statusFilter
    ) {
        MistakePassportResponseDto response = readModelService.getLearnerMistakePassport(
            principal.learnerId(),
            statusFilter
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate())
            .body(response);
    }
}
