package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.SettingsResponse;
import com.humanworkstream.baha.dto.UpdateSettingsRequest;
import com.humanworkstream.baha.entity.AppSetting;
import com.humanworkstream.baha.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private static final String CHAT_ENABLED_KEY = "chat_enabled";

    private final AppSettingRepository repository;

    /** Reads the current settings. A missing row (fresh DB before the seed insert lands, or a
     *  key that was never set) defaults to enabled — a read failure must never silently hide chat. */
    @Transactional(readOnly = true)
    public SettingsResponse get() {
        boolean chatEnabled = repository.findById(CHAT_ENABLED_KEY)
                .map(s -> !"false".equalsIgnoreCase(s.getValue()))
                .orElse(true);
        return new SettingsResponse(chatEnabled);
    }

    /** Upserts only the fields present in the request (null = unchanged), then returns the
     *  resulting settings. */
    @Transactional
    public SettingsResponse patch(UpdateSettingsRequest req) {
        if (req.chatEnabled() != null) {
            AppSetting setting = repository.findById(CHAT_ENABLED_KEY).orElseGet(AppSetting::new);
            setting.setKey(CHAT_ENABLED_KEY);
            setting.setValue(String.valueOf(req.chatEnabled()));
            repository.save(setting);
        }
        return get();
    }
}
