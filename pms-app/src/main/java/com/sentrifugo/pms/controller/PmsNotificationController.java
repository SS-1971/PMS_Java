package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.PmsNotificationReadRequest;
import com.sentrifugo.pms.model.PmsNotificationResponse;
import com.sentrifugo.pms.service.PmsNotificationService;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The bell: the signed-in user's PMS notifications. Any signed-in user may read their own. */
@RestController
@RequestMapping("${pms.api.base.path}/pms-notification")
@RequiredArgsConstructor
public class PmsNotificationController {

    private final PmsNotificationService service;

    @GetMapping("/get/my")
    @Operation(summary = "My notifications",
            description = "The latest 50 notifications, newest first, with the unread count.")
    public ResponseEntity<ApiResponse<PmsNotificationResponse>> mine(@AuthenticationPrincipal PmsUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(service.mine(user), "Notifications found"));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark notifications read",
            description = "ids lists the notifications to mark; leave it empty to mark every unread one.")
    public ResponseEntity<ApiResponse<PmsNotificationResponse>> read(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestBody(required = false) PmsNotificationReadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.markRead(user, request), "Marked read"));
    }
}
