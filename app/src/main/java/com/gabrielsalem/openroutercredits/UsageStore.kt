package com.gabrielsalem.openroutercredits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Armazena localmente o uso acumulado da OpenRouter para montar o gráfico de 24h.
 * A OpenRouter não expõe histórico via API, então derivamos os deltas a cada poll:
 * usage_delta = total_usage_atual - total_usage_anterior.
 */
object UsageStore {

    private const val FILE = "usage_24h.json"
    private const val WINDOW_MS = 24L * 60 * 60 * 1000 // 24h

    private data class Point(val t: Long, val total: Double)

    fun record(context: Context, totalUsage: Double) {
        val points = load(context).toMutableList()
        val now = System.currentTimeMillis()
        val last = points.lastOrNull()
        if (last != null && totalUsage < last.total) {
            // resetou (conta recarregada) — descarta o ponto antigo p/ não gerar delta negativo
            points.clear()
        }
        points.add(Point(now, totalUsage))
        // poda janela de 24h
        val cutoff = now - WINDOW_MS
        val pruned = points.filter { it.t >= cutoff }
        save(context, pruned)
    }

    /** Retorna pares (epochMs, gastoNoIntervalo) para as últimas 24h, prontos p/ sparkline. */
    fun series(context: Context): List<Pair<Long, Double>> {
        val points = load(context)
        if (points.size < 2) return emptyList()
        val out = mutableListOf<Pair<Long, Double>>()
        for (i in 1 until points.size) {
            val delta = max(0.0, points[i].total - points[i - 1].total)
            out.add(points[i].t to delta)
        }
        return out
    }

    fun total24h(context: Context): Double =
        series(context).sumOf { it.second }

    private fun load(context: Context): List<Point> {
        return try {
            val txt = context.openFileInput(FILE).bufferedReader().use { it.readText() }
            val arr = JSONArray(txt)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Point(o.getLong("t"), o.getDouble("total"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, points: List<Point>) {
        try {
            val arr = JSONArray()
            points.forEach { p ->
                arr.put(JSONObject().apply {
                    put("t", p.t)
                    put("total", p.total)
                })
            }
            context.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(arr.toString().toByteArray()) }
        } catch (_: Exception) {
            // falha de I/O silenciosa — gráfico fica vazio, widget continua funcional
        }
    }
}
