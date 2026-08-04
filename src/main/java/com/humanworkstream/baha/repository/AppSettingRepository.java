package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
