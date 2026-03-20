package com.drostwades.errorsound

object AlertMonitoring {

    fun shouldMonitor(settings: AlertSettings.State, kind: ErrorKind): Boolean {
        if (!settings.enabled) return false
        return isKindEnabled(settings, kind)
    }

    fun isKindEnabled(settings: AlertSettings.State, kind: ErrorKind): Boolean {
        return when (kind) {
            ErrorKind.CONFIGURATION -> settings.monitorConfiguration
            ErrorKind.COMPILATION -> settings.monitorCompilation
            ErrorKind.TEST_FAILURE -> settings.monitorTestFailure
            ErrorKind.NETWORK -> settings.monitorNetwork
            ErrorKind.EXCEPTION -> settings.monitorException
            ErrorKind.GENERIC -> settings.monitorGeneric
            ErrorKind.SUCCESS -> settings.monitorSuccess
            ErrorKind.NONE -> false
        }
    }

    fun setKindEnabled(settings: AlertSettings.State, kind: ErrorKind, enabled: Boolean) {
        when (kind) {
            ErrorKind.CONFIGURATION -> settings.monitorConfiguration = enabled
            ErrorKind.COMPILATION -> settings.monitorCompilation = enabled
            ErrorKind.TEST_FAILURE -> settings.monitorTestFailure = enabled
            ErrorKind.NETWORK -> settings.monitorNetwork = enabled
            ErrorKind.EXCEPTION -> settings.monitorException = enabled
            ErrorKind.GENERIC -> settings.monitorGeneric = enabled
            ErrorKind.SUCCESS -> settings.monitorSuccess = enabled
            ErrorKind.NONE -> Unit
        }
    }
}
