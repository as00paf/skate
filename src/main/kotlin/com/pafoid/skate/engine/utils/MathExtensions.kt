package com.pafoid.skate.engine.utils

import kotlin.math.PI

fun Float.toRadiansF(): Float = this / 180.0f * PI.toFloat()

fun Float.toDegreesF(): Float = this * 180.0f / PI.toFloat()