package com.avandocmsg.messenger.mobile.sdk.capabilities

import com.avandocmsg.messenger.mobile.sdk.model.CapabilitiesDto

object CapabilityGating {
    fun isEnabled(cap: CapabilitiesDto, capability: String): Boolean =
        cap.capabilities.contains(capability)

    fun isAddonEnabled(cap: CapabilitiesDto, addonId: String): Boolean =
        cap.addons[addonId]?.enabled == true
}
