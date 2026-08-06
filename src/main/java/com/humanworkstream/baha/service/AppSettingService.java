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
    private static final String SOCIAL_AUTOPOST_FACEBOOK_KEY = "social_autopost_facebook";
    private static final String SOCIAL_AUTOPOST_X_KEY = "social_autopost_x";

    private final AppSettingRepository repository;

    /** Reads the current settings. A missing row (fresh DB before the seed insert lands, or a
     *  key that was never set) defaults to enabled — a read failure must never silently hide chat
     *  or auto-post. */
    @Transactional(readOnly = true)
    public SettingsResponse get() {
        boolean chatEnabled = isEnabled(CHAT_ENABLED_KEY);
        boolean socialAutopostFacebook = isEnabled(SOCIAL_AUTOPOST_FACEBOOK_KEY);
        boolean socialAutopostX = isEnabled(SOCIAL_AUTOPOST_X_KEY);
        return new SettingsResponse(chatEnabled, socialAutopostFacebook, socialAutopostX);
    }

    /** True unless the key exists and is explicitly "false". */
    @Transactional(readOnly = true)
    public boolean isEnabled(String key) {
        return repository.findById(key)
                .map(s -> !"false".equalsIgnoreCase(s.getValue()))
                .orElse(true);
    }

    /** Upserts only the fields present in the request (null = unchanged), then returns the
     *  resulting settings. */
    @Transactional
    public SettingsResponse patch(UpdateSettingsRequest req) {
        upsert(CHAT_ENABLED_KEY, req.chatEnabled());
        upsert(SOCIAL_AUTOPOST_FACEBOOK_KEY, req.socialAutopostFacebook());
        upsert(SOCIAL_AUTOPOST_X_KEY, req.socialAutopostX());
        return get();
    }

    private void upsert(String key, Boolean value) {
        if (value == null) return;
        AppSetting setting = repository.findById(key).orElseGet(AppSetting::new);
        setting.setKey(key);
        setting.setValue(String.valueOf(value));
        repository.save(setting);
    }
}
