package com.sentrifugo.pms.controller;

import com.sentrifugo.security.context.PmsUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    /** Returns the authenticated caller resolved from the IAM session. */
    @GetMapping("/me")
    public PmsUserPrincipal me(@AuthenticationPrincipal PmsUserPrincipal user) {
        return user;
    }
}
