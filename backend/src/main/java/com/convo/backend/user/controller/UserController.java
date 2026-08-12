package com.convo.backend.user.controller;

import com.convo.backend.auth.dto.PublicUserResponse;
import com.convo.backend.auth.dto.UserBatchRequest;
import com.convo.backend.user.service.UserLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately NOT under /api/auth/** — that prefix is permitAll in
 * WebAndSecurityConfig (signup/login must be reachable pre-token), whereas
 * looking up another user's name should require a valid session like every
 * other endpoint (anyRequest().authenticated() covers this path already).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserLookupService service;

    public UserController(UserLookupService service) {
        this.service = service;
    }

    // GET /api/users/{id} — single lookup, e.g. resolving one chain hop.
    @GetMapping("/{id}")
    public PublicUserResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    // POST /api/users/batch — resolve every hop in a trace in one round trip
    // instead of one request per distinct sender. Body: { "ids": [uuid, ...] }.
    @PostMapping("/batch")
    public List<PublicUserResponse> getByIds(@Valid @RequestBody UserBatchRequest request) {
        return service.getByIds(request.ids());
    }
}