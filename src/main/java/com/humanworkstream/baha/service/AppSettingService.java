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
    /** Global "rain is falling now" flag that flips the predictive water-level model's direction
     *  (baha-ui's projectDepth). Unlike the other flags, a missing row must default to OFF: rain
     *  is the exception, not the steady state, and defaulting it on would make every report look
     *  like it's rising the moment the seed insert hasn't landed yet. */
    private static final String RAIN_ACTIVE_KEY = "rain_active";

    private final AppSettingRepository repository;

    /** Reads the current settings. A missing row (fresh DB before the seed insert lands, or a
     *  key that was never set) defaults to enabled — a read failure must never silently hide chat
     *  or auto-post. {@code rain_active} is the one exception and defaults to off (see its key's
     *  javadoc). */
    @Transactional(readOnly = true)
    public SettingsResponse get() {
        boolean chatEnabled = isEnabled(CHAT_ENABLED_KEY);
        boolean socialAutopostFacebook = isEnabled(SOCIAL_AUTOPOST_FACEBOOK_KEY);
        boolean socialAutopostX = isEnabled(SOCIAL_AUTOPOST_X_KEY);
        boolean rainActive = isEnabled(RAIN_ACTIVE_KEY, false);
        return new SettingsResponse(chatEnabled, socialAutopostFacebook, socialAutopostX, rainActive);
    }

    /** True unless the key exists and is explicitly "false". A missing key defaults to enabled —
     *  use {@link #isEnabled(String, boolean)} where a missing key should default to off. */
    @Transactional(readOnly = true)
    public boolean isEnabled(String key) {
        return isEnabled(key, true);
    }

    /** True unless the key exists and is explicitly "false"; a missing key defaults to
     *  {@code fallback} rather than always defaulting to enabled. */
    @Transactional(readOnly = true)
    public boolean isEnabled(String key, boolean fallback) {
        return repository.findById(key)
                .map(s -> !"false".equalsIgnoreCase(s.getValue()))
                .orElse(fallback);
    }

    /** Upserts only the fields present in the request (null = unchanged), then returns the
     *  resulting settings. */
    @Transactional
    public SettingsResponse patch(UpdateSettingsRequest req) {
        upsert(CHAT_ENABLED_KEY, req.chatEnabled());
        upsert(SOCIAL_AUTOPOST_FACEBOOK_KEY, req.socialAutopostFacebook());
        upsert(SOCIAL_AUTOPOST_X_KEY, req.socialAutopostX());
        upsert(RAIN_ACTIVE_KEY, req.rainActive());
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
