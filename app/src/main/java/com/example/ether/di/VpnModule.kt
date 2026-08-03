package com.example.ether.di

import com.example.ether.browser.vpn.VpnManager
import com.example.ether.browser.vpn.WireGuardConfigManager
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.data.repository.VpnRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VpnModule {
    
    @Provides
    @Singleton
    fun provideWireGuardConfigManager(settingsRepository: SettingsRepository): WireGuardConfigManager {
        return WireGuardConfigManager(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideVpnManager(
        @ApplicationContext context: android.content.Context,
        settingsRepository: SettingsRepository,
        vpnRepository: VpnRepository
    ): VpnManager {
        return VpnManager(context, settingsRepository, vpnRepository)
    }
}
