package com.catrescue.api.tracking.controller;

import com.catrescue.api.tracking.dto.VolunteerNotificationResponse;
import com.catrescue.api.tracking.service.VolunteerNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Volunteer-facing notification inbox (in-app messages first).
 */
@RestController
@RequestMapping("/api/v1/volunteers")
public class VolunteerNotificationController {

    private final VolunteerNotificationService volunteerNotificationService;

    public VolunteerNotificationController(VolunteerNotificationService volunteerNotificationService) {
        this.volunteerNotificationService = volunteerNotificationService;
    }

    @GetMapping("/notifications")
    public List<VolunteerNotificationResponse> list(@RequestParam(name = "userId") long volunteerUserId) {
        return volunteerNotificationService.listForVolunteer(volunteerUserId);
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> acknowledge(
            @PathVariable("id") long notificationId,
            @RequestParam(name = "userId") long volunteerUserId
    ) {
        try {
            volunteerNotificationService.acknowledge(volunteerUserId, notificationId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
