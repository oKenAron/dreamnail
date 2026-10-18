package io.github.okenaron.dreamnail.sleeptimer

/**
 * 把剩余毫秒数格式化成 `分:秒`（例如 `29:59`）。
 *
 * 秒数往上取整：还剩 59.001 秒时显示 `1:00`，这样到点那一刻正好变成 `0:00`。
 * 负数（已经过了结束时刻）按 0 处理。超过 60 分钟也不换成小时，例如 `120:00`。
 */
internal fun formatRemaining(remainingMillis: Long): String {
    val seconds = (remainingMillis.coerceAtLeast(0) + 999) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
