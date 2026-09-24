package com.nungil.contract.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide scope for database writes. A write launched in a fragment scope is cancelled when the
 * screen closes and silently never lands.
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
