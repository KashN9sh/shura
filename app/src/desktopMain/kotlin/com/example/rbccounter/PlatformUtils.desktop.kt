package com.example.rbccounter

import java.util.UUID

actual fun randomId(): String = UUID.randomUUID().toString()
