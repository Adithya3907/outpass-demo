package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_setting")
@Getter @Setter @NoArgsConstructor
public class AppSetting {
    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String value;

    public AppSetting(String key, String value) { this.key = key; this.value = value; }
}
