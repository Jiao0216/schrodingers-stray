package com.catrescue.api.admin.web;

import com.catrescue.api.tracking.service.ClipThresholdCalibrationService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inspect / refresh the CLIP same-cat distance threshold fitted from volunteer feedback.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/admin/clip-calibration")
public class ClipCalibrationController {

    private final ClipThresholdCalibrationService calibrationService;

    public ClipCalibrationController(ClipThresholdCalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    @GetMapping
    public ClipThresholdCalibrationService.Status status() {
        return calibrationService.status();
    }

    @PostMapping
    public ClipThresholdCalibrationService.Status recalibrate() {
        return calibrationService.recalibrate();
    }
}
