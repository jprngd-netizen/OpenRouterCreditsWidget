package com.gabrielsalem.openroutercredits

import android.content.Context
import com.google.gson.Gson
import kotlin.math.max
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Armazena localmente o uso acumulado da OpenRouter para montar o gráfico de 24h.
 * O gráfico é semeado com os dados diários da /activity e refinado com deltas
 * derivados a cada poll (15 min): usage_delta = total_usage_atual - total_usage_anterior.
 */
data class UsagePoint(val t: Long, val total: Double)

object UsageStore {

    private const val FILE = "usage_24h.json"
    private const val WINDOW_MS = 24L * 60 * 60 * 1000 // 24h
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Registra um ponto de uso atual. */
    fun record(context: Context, totalUsage: Double) {
        val points = load(context).toMutableList()
        val now = System.currentTimeMillis()
        val last = points.lastOrNull()
        if (last != null && totalUsage < last.total) {
            // resetou (conta recarregada) — descarta o ponto antigo p/ não gerar delta negativo
            points.clear()
        }
        points.add(UsagePoint(now, totalUsage))
        savePruned(context, points, now)
    }

    /**
     * Semeia o histórico com dados da /activity quando o armazenamento local
     * está vazio ou tem poucos pontos. Constrói pontos de uso acumulado a
     * partir dos totais diários, começando do total_usage atual e subtraindo
     * cada dia para trás.
     */
    fun seedFromActivity(context: Context, activityItems: List<ActivityItem>, currentTotalUsage: Double) {
        val existing = load(context)
        // Só semeia se tiver 0 ou 1 ponto (acabou de instalar ou foi limpo)
        if (existing.size >= 2) return

        val byDate = activityItems.groupBy { it.date }
            .mapValues { (_, v) -> v.sumOf { it.usage } }
            .filterKeys { date ->
                // Só datas das últimas 72h (para cobrir o gráfico de 24h com margem)
                try {
                    val d = LocalDate.parse(date, dateFmt)
                    val now = LocalDate.now()
                    !d.isAfter(now) && d.isAfter(now.minusDays(3))
                } catch (_: Exception) { false }
            }
            .entries
            .sortedByDescending { it.key } // mais recente primeiro

        if (byDate.isEmpty()) return

        val now = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val points = mutableListOf<UsagePoint>()

        var cumulative = currentTotalUsage
        for ((date, dailyUsage) in byDate) {
            val ld = LocalDate.parse(date, dateFmt)
            val midnight = ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
            if (midnight > now) continue
            points.add(UsagePoint(midnight, cumulative))
            cumulative -= dailyUsage
        }

        // Ponto atual (já incluso)
        points.add(UsagePoint(now, currentTotalUsage))

        val pruned = points.filter { it.t >= now - WINDOW_MS }
        if (pruned.size > existing.size) {
            save(context, pruned)
        }
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

    private fun load(context: Context): List<UsagePoint> {
        return try {
            val txt = context.openFileInput(FILE).bufferedReader().use { it.readText() }
            Gson().fromJson(txt, Array<UsagePoint>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, points: List<UsagePoint>) {
        try {
            val json = Gson().toJson(points)
            context.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            android.util.Log.w("UsageStore", "failed to persist", e)
        }
    }

    private fun savePruned(context: Context, points: List<UsagePoint>, now: Long) {
        val cutoff = now - WINDOW_MS
        save(context, points.filter { it.t >= cutoff })
    }
}
