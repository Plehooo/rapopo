package com.bittv.iptv.util

import android.content.Context
import com.bittv.iptv.social.FirebaseIdentity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin

/**
 * In-app stock-market simulator. It uses only virtual points and has no cash-out path.
 * Prices are deterministic per day/hour, so every user sees a consistent market cycle.
 */
object VirtualMarketManager {
    data class Quote(val symbol: String, val name: String, val sector: String, val price: Int, val changePct: Double)
    data class Holding(var shares: Int, var avgCost: Double)

    private data class Seed(val symbol: String, val name: String, val sector: String, val base: Int, val phase: Double)

    private val seeds = listOf(
        Seed("BITV", "BITTV Network", "Media", 120, 0.2),
        Seed("RAPO", "RAPO Digital", "Tech", 180, 1.1),
        Seed("NUSA", "Nusantara Cloud", "Cloud", 95, 2.3),
        Seed("LUME", "Lumen Studio", "Creative", 72, 3.4),
        Seed("KOTA", "Kota Mart", "Retail", 140, 4.5),
        Seed("ARCA", "Arca Energy", "Energy", 210, 5.1),
        Seed("JAYA", "Jaya Food", "Food", 88, 0.9),
        Seed("ORBI", "Orbit Labs", "Science", 260, 2.8)
    )

    fun quotes(now: Long = System.currentTimeMillis()): List<Quote> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        // Keep the offline quote formula identical to functions/index.js so
        // the UI does not show a visibly different price before server refresh.
        val day = now.floorDiv(86_400_000L)
        val hour = now.floorDiv(3_600_000L)
        return seeds.map { seed ->
            val wave = sin(day * 0.47 + hour * 0.23 + seed.phase)
            val trend = sin(day * 0.07 + seed.phase * 0.8) * 0.10
            val change = (wave * 0.08 + trend)
            val price = (seed.base * (1.0 + change)).toInt().coerceAtLeast(10)
            Quote(seed.symbol, seed.name, seed.sector, price, change * 100.0)
        }
    }

    fun price(symbol: String): Int = quotes().firstOrNull { it.symbol == symbol }?.price ?: 0

    fun holdings(context: Context, snapshot: DataSnapshot? = null): MutableMap<String, Holding> {
        val result = linkedMapOf<String, Holding>()
        snapshot?.children?.forEach { node ->
            val shares = (node.child("shares").value as? Number)?.toInt() ?: 0
            val avg = (node.child("avgCost").value as? Number)?.toDouble() ?: 0.0
            if (shares > 0) result[node.key.orEmpty()] = Holding(shares, avg)
        }
        return result
    }

    fun sync(context: Context, holdings: Map<String, Holding>, onDone: (Boolean) -> Unit = {}) {
        val auth = FirebaseIdentity.auth(context)
        val db = FirebaseIdentity.database(context)
        val uid = auth?.currentUser?.uid
        if (db == null || uid.isNullOrBlank()) {
            onDone(false)
            return
        }
        val data = holdings.mapValues { mapOf("shares" to it.value.shares, "avgCost" to it.value.avgCost) }
        db.getReference("virtualMarket").child(uid).setValue(data)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun load(context: Context, onLoaded: (MutableMap<String, Holding>) -> Unit) {
        val auth = FirebaseIdentity.auth(context)
        val db = FirebaseIdentity.database(context)
        val uid = auth?.currentUser?.uid
        if (db == null || uid.isNullOrBlank()) {
            onLoaded(loadLocal(context))
            return
        }
        db.getReference("virtualMarket").child(uid).get()
            .addOnSuccessListener { snap ->
                val data = holdings(context, snap)
                if (data.isNotEmpty()) saveLocal(context, data)
                onLoaded(if (data.isNotEmpty()) data else loadLocal(context))
            }
            .addOnFailureListener { onLoaded(loadLocal(context)) }
    }

    fun buy(context: Context, portfolio: MutableMap<String, Holding>, symbol: String, shares: Int = 1): Boolean {
        if (shares <= 0) return false
        val quote = quotes().firstOrNull { it.symbol == symbol } ?: return false
        val cost = quote.price.toLong() * shares
        if (cost > Int.MAX_VALUE || !PointsManager.spendPoints(context, cost.toInt())) return false
        val old = portfolio[symbol]
        val oldShares = old?.shares ?: 0
        val newShares = oldShares + shares
        val avg = if (oldShares <= 0) quote.price.toDouble() else ((old!!.avgCost * oldShares) + quote.price * shares) / newShares
        portfolio[symbol] = Holding(newShares, avg)
        saveLocal(context, portfolio)
        sync(context, portfolio)
        return true
    }

    fun sell(context: Context, portfolio: MutableMap<String, Holding>, symbol: String, shares: Int = 1): Boolean {
        val holding = portfolio[symbol] ?: return false
        if (shares <= 0 || holding.shares < shares) return false
        val price = price(symbol)
        portfolio[symbol] = Holding(holding.shares - shares, holding.avgCost)
        if (portfolio[symbol]?.shares == 0) portfolio.remove(symbol)
        PointsManager.addPoints(context, price * shares)
        saveLocal(context, portfolio)
        sync(context, portfolio)
        return true
    }

    fun saveLocal(context: Context, holdings: Map<String, Holding>) {
        val prefs = context.getSharedPreferences("bittv_virtual_market", Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        holdings.forEach { (symbol, holding) ->
            editor.putInt("${symbol}_shares", holding.shares)
            editor.putString("${symbol}_avg", holding.avgCost.toString())
        }
        editor.apply()
    }

    fun loadLocal(context: Context): MutableMap<String, Holding> {
        val prefs = context.getSharedPreferences("bittv_virtual_market", Context.MODE_PRIVATE)
        val result = linkedMapOf<String, Holding>()
        seeds.forEach { seed ->
            val shares = prefs.getInt("${seed.symbol}_shares", 0)
            if (shares > 0) result[seed.symbol] = Holding(shares, prefs.getString("${seed.symbol}_avg", "0")?.toDoubleOrNull() ?: 0.0)
        }
        return result
    }

    fun portfolioValue(holdings: Map<String, Holding>): Int = holdings.entries.sumOf { (symbol, holding) -> price(symbol) * holding.shares }
    fun investedValue(holdings: Map<String, Holding>): Int = holdings.entries.sumOf { (_, holding) -> (holding.avgCost * holding.shares).toInt() }
    fun dayStamp(): String {
        val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Jakarta"), java.util.Locale.US)
        return "%04d-%03d".format(c.get(Calendar.YEAR), c.get(Calendar.DAY_OF_YEAR))
    }
}
