package com.gabrielsalem.openroutercredits

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Processa a resposta de /activity (granularidade DIÁRIA) e deriva os valores
 * exibidos no widget: gasto de hoje, total dos últimos 7 dias e a série 7d.
 */
object ActivityStore {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Soma o usage (USD) de todos os itens cuja data == hoje. */
    fun spentToday(items: List<ActivityItem>): Double {
        val today = LocalDate.now().format(fmt)
        return items.filter { it.date == today }.sumOf { it.usage }
    }

    /** Retorna os últimos 7 dias (mais antigo -> hoje) com o total gasto por dia. */
    fun last7Days(items: List<ActivityItem>): List<Pair<String, Double>> {
        val byDate = items.groupBy { it.date }
            .mapValues { (_, v) -> v.sumOf { it.usage } }
        val out = mutableListOf<Pair<String, Double>>()
        val today = LocalDate.now()
        for (i in 6 downTo 0) {
            val d = today.minusDays(i.toLong())
            val key = d.format(fmt)
            out.add(key to (byDate[key] ?: 0.0))
        }
        return out
    }

    /** Top modelos por gasto nas últimas 24-48h (itens do dia atual + ontem). */
    fun topModels(items: List<ActivityItem>, limit: Int = 3): List<Pair<String, Double>> {
        val today = LocalDate.now().format(fmt)
        val yest = LocalDate.now().minusDays(1).format(fmt)
        return items.filter { it.date == today || it.date == yest }
            .groupBy { it.model }
            .mapValues { (_, v) -> v.sumOf { it.usage } }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }
}
