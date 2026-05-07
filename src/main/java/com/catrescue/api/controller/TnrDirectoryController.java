package com.catrescue.api.controller;

import com.catrescue.api.dto.TnrLocationDto;
import com.catrescue.api.service.TnrDirectoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tnr-directory")
public class TnrDirectoryController {

    private static final int MAX_LIMIT = 50;

    private final TnrDirectoryService tnrDirectoryService;

    public TnrDirectoryController(TnrDirectoryService tnrDirectoryService) {
        this.tnrDirectoryService = tnrDirectoryService;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/nearest")
    public List<TnrLocationDto> nearest(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") int limit) {
        int n = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return tnrDirectoryService.nearestDtos(lat, lng, n);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/{id:[a-zA-Z0-9._-]+}")
    public TnrLocationDto byId(
            @PathVariable String id,
            @RequestParam double lat,
            @RequestParam double lng) {
        return tnrDirectoryService.findDtoById(id, lat, lng)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown TNR id: " + id));
    }
}
