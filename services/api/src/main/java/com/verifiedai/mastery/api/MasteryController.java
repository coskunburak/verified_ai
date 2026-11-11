package com.verifiedai.mastery.api;

import com.verifiedai.mastery.MasteryApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mastery/skills")
public class MasteryController {
    private final MasteryApplicationService service;
    public MasteryController(MasteryApplicationService service) { this.service=service; }
    @GetMapping
    public MasteryApplicationService.View read(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="1") int representationVersion) {
        return service.read(AuthenticatedUser.from(jwt).userId(), representationVersion);
    }
}
