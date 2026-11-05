package com.verifiedai.visualization.api;
import com.verifiedai.visualization.application.VisualizationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{problemId}/visualizations")
public class VisualizationController {
    private static final ObjectMapper WIRE=new ObjectMapper();
    private final VisualizationService service;
    public VisualizationController(VisualizationService service) { this.service=service; }
    @GetMapping("/context")
    public Map<String,Object> context(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return wire(service.context(AuthenticatedUser.from(jwt).userId(),problemId));
    }
    @PostMapping
    public Map<String,Object> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID problemId,@RequestBody Map<String,Object> body,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return wire(service.visualize(AuthenticatedUser.from(jwt).userId(),problemId,WIRE.valueToTree(body).toString()));
    }
    private static Map<String,Object> wire(Object value) {
        return WIRE.convertValue(value,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
    }
}
