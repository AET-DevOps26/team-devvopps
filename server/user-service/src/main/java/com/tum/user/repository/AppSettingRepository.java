package com.tum.user.repository;

import com.tum.user.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for runtime app settings. */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
