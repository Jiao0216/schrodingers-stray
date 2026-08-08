package com.catrescue.api.tracking.controller;

import com.catrescue.api.tracking.dto.ShelterInfoResponse;
import com.catrescue.api.tracking.service.ShelterInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/shelter-info", "/shelter-info"})
public class ShelterInfoController {

    private final ShelterInfoService shelterInfoService;

    public ShelterInfoController(ShelterInfoService shelterInfoService) {
        this.shelterInfoService = shelterInfoService;
    }

    @GetMapping("/nearest")
    public List<ShelterInfoResponse> nearest(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "service", required = false) String service
    ) {
        return shelterInfoService.nearest(lat, lng, limit, service);
    }

    @GetMapping("/{id}")
    public ShelterInfoResponse get(
            @PathVariable("id") long id,
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng
    ) {
        return shelterInfoService.getById(id, lat, lng);
    }
}
