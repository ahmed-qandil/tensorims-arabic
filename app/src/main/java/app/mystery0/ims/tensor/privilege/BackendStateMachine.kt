package app.mystery0.ims.tensor.privilege

import app.mystery0.ims.tensor.R

/** 不依赖 Android 的安全状态机，由统一调度器串行调用。 */
class BackendStateMachine(mode: BackendMode = BackendMode.UNSET, unresolved: Boolean = false) {
    @Volatile var status = BackendStatus(
        mode = mode,
        connection = if (unresolved) ConnectionState.RECOVERY_REQUIRED else ConnectionState.DISCONNECTED,
        errorCode = if (unresolved) "RECOVERY_REQUIRED" else null,
        message = if (unresolved) BackendStrings.get(R.string.err_prev_op_unconfirmed) else null,
    )
        private set
    @Volatile var activeOperationId: String? = null
        private set

    @Synchronized fun choose(mode: BackendMode): String? {
        if (freezeForSwitch() == null) return BackendStrings.get(R.string.err_op_unfinished_switch)
        completeSwitch(mode)
        return null
    }

    /** 在任何解绑/关闭副作用之前原子冻结，继承锁上下文的后台任务也不能抢写。 */
    @Synchronized fun freezeForSwitch(): BackendStatus? {
        if (activeOperationId != null || status.connection in setOf(ConnectionState.BUSY,
                ConnectionState.RECOVERY_REQUIRED, ConnectionState.SWITCHING, ConnectionState.CONNECTING)) return null
        val previous = status
        status = status.copy(connection = ConnectionState.SWITCHING)
        return previous
    }

    @Synchronized fun completeSwitch(mode: BackendMode): Boolean {
        if (activeOperationId != null || status.connection != ConnectionState.SWITCHING) return false
        status = BackendStatus(mode = mode, epoch = status.epoch + 1,
            connection = if (mode == BackendMode.UNSET) ConnectionState.DISCONNECTED else ConnectionState.CONNECTING)
        return true
    }

    @Synchronized fun abortSwitch(previous: BackendStatus, code: String, message: String) {
        if (activeOperationId == null && status.connection == ConnectionState.SWITCHING &&
            status.epoch == previous.epoch && status.mode == previous.mode) {
            status = previous.copy(connection = if (previous.mode == BackendMode.UNSET)
                ConnectionState.DISCONNECTED else ConnectionState.BLOCKED, errorCode = code, message = message)
        }
    }

    /** 闲置停止不改变用户选定模式，但递增代际使旧队列请求失效。 */
    @Synchronized fun completeIdleStop(): Boolean {
        if (status.mode != BackendMode.EMBEDDED || status.connection != ConnectionState.SWITCHING || activeOperationId != null) return false
        status = BackendStatus(mode = BackendMode.EMBEDDED, connection = ConnectionState.DISCONNECTED,
            epoch = status.epoch + 1, errorCode = "EMBEDDED_UNAVAILABLE", message = BackendStrings.get(R.string.err_start_embedded_service))
        return true
    }

    @Synchronized fun beginConnection(): Boolean {
        if (activeOperationId != null || status.mode == BackendMode.UNSET || status.connection in setOf(
                ConnectionState.BUSY, ConnectionState.RECOVERY_REQUIRED, ConnectionState.SWITCHING, ConnectionState.CONNECTING)) return false
        status = status.copy(connection = ConnectionState.CONNECTING)
        return true
    }

    @Synchronized fun updateConnection(value: BackendStatus) {
        if (value.mode != status.mode || value.epoch != status.epoch) return
        if (activeOperationId != null || status.connection == ConnectionState.RECOVERY_REQUIRED) return
        status = value
    }

    @Synchronized fun begin(operationId: String): Boolean {
        if (status.mode == BackendMode.UNSET || !status.isReady || activeOperationId != null) return false
        activeOperationId = operationId
        status = status.copy(connection = ConnectionState.BUSY, errorCode = null, message = null)
        return true
    }

    @Synchronized fun mayContinue(operationId: String, epoch: Long): Boolean =
        operationId == activeOperationId && epoch == status.epoch && status.connection == ConnectionState.BUSY

    @Synchronized fun uncertain(operationId: String, epoch: Long) {
        if (operationId != activeOperationId || epoch != status.epoch) return
        status = status.copy(connection = ConnectionState.RECOVERY_REQUIRED, errorCode = "OPERATION_INDETERMINATE",
            message = BackendStrings.get(R.string.err_result_unconfirmed_no_retry))
    }

    @Synchronized fun finish(operationId: String, epoch: Long, cleanupConfirmed: Boolean): Boolean {
        if (operationId != activeOperationId || epoch != status.epoch) return false
        if (!cleanupConfirmed) {
            uncertain(operationId, epoch)
            return false
        }
        activeOperationId = null
        if (status.connection != ConnectionState.RECOVERY_REQUIRED) {
            status = status.copy(connection = ConnectionState.READY, errorCode = null, message = null)
        }
        return true
    }

    @Synchronized fun reconcile(terminalConfirmed: Boolean, authenticated: Boolean, readBackConfirmed: Boolean): Boolean {
        if (activeOperationId != null || !terminalConfirmed || !authenticated || !readBackConfirmed) return false
        if (status.mode == BackendMode.UNSET) return false
        status = status.copy(connection = ConnectionState.READY, errorCode = null,
            message = BackendStrings.get(R.string.err_state_checked_auto_restore_paused))
        return true
    }
}
