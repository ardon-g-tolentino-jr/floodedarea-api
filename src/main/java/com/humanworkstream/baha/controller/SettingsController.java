package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.SettingsResponse;
import com.humanworkstream.baha.dto.UpdateSettingsRequest;
import com.humanworkstream.baha.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global app settings (currently just community-chat visibility). Reading is public; patching
 *  requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppSettingService service;

    @GetMapping
    public SettingsResponse get() {
        return service.get();
    }

    @PatchMapping
    public SettingsResponse patch(@RequestBody UpdateSettingsRequest req) {
        return service.patch(req);
    }
}
