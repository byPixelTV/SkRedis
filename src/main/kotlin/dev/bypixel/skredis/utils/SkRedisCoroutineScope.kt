package dev.bypixel.skredis.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object SkRedisCoroutineScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)