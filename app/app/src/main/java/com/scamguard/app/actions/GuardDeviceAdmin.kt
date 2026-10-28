package com.scamguard.app.actions

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.scamguard.app.R

/**
 * Device admin with the single force-lock policy, used only by Demo Mode to lock the
 * screen after a red flag. Activation always goes through the system consent screen.
 */
class GuardDeviceAdmin : DeviceAdminReceiver() {

    companion object {
        fun component(context: Context) = ComponentName(context, GuardDeviceAdmin::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component(context)) == true

        /** The system screen where the user can approve the force-lock policy. */
        fun activationIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))

        fun deactivate(context: Context) {
            context.getSystemService(DevicePolicyManager::class.java)?.removeActiveAdmin(component(context))
        }

        /** Locks the screen if the user approved the policy. Returns false otherwise. */
        fun lockNow(context: Context): Boolean {
            if (!isActive(context)) return false
            return try {
                context.getSystemService(DevicePolicyManager::class.java)?.lockNow()
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
