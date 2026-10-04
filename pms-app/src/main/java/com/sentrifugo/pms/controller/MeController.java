package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    @GetMapping("/me")
    @Operation(summary = "Get current user",
            description = "Returns the authenticated caller resolved from the IAM session, including organisation and permissions.")
    public ResponseEntity<ApiResponse<PmsUserPrincipal>> me(@AuthenticationPrincipal PmsUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(user, "Current user retrieved successfully"));
    }
}
