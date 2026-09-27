package com.bittv.iptv.util

import android.content.Context
import com.bittv.iptv.social.FirebaseIdentity
import com.google.firebase.functions.FirebaseFunctions

/**
 * Server-authoritative virtual economy. Coins have no cash value and cannot be withdrawn.
 * The callable functions keep transfers and market trades off the client trust boundary.
 */
object CloudEconomyManager {
    data class Wallet(val coins: Long, val streak: Int = 0)
    data class TradeResult(val coins: Long, val shares: Int, val avgCost: Double, val price: Long)
    data class TransferResult(val senderCoins: Long, val amount: Long)
    data class DailyResult(val coins: Long, val reward: Long, val streak: Int)
    data class MarketQuote(val symbol: String, val name: String, val sector: String, val price: Long, val changePct: Double)
    data class PortfolioHolding(val shares: Int, val avgCost: Double)
    data class Portfolio(val coins: Long, val holdings: Map<String, PortfolioHolding>)
    data class MabarReward(val coins: Long, val reward: Long, val role: String)
    data class MabarMove(val board: String, val status: String, val turn: String)

    private const val REGION = "asia-southeast2"

    private fun functions(context: Context): FirebaseFunctions? =
        FirebaseIdentity.app(context)?.let { FirebaseFunctions.getInstance(it, REGION) }

    fun ensureAuth(context: Context, onReady: (Boolean) -> Unit) =
        FirebaseIdentity.ensureAnonymous(context) { onReady(it?.currentUser != null) }

    fun bootstrap(context: Context, onDone: (Result<Wallet>) -> Unit) {
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("initializeWallet")?.call()
                ?.addOnSuccessListener { result -> onDone(runCatching { parseWallet(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun getWallet(context: Context, onDone: (Result<Wallet>) -> Unit) {
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("getWallet")?.call()
                ?.addOnSuccessListener { onDone(runCatching { parseWallet(resultData = it.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun claimDaily(context: Context, onDone: (Result<DailyResult>) -> Unit) {
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("claimDailyCoins")?.call()
                ?.addOnSuccessListener { result -> onDone(runCatching { parseDaily(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun transfer(context: Context, targetUid: String, amount: Long, onDone: (Result<TransferResult>) -> Unit) {
        if (amount <= 0L) {
            onDone(Result.failure(IllegalArgumentException("Jumlah harus lebih dari 0")))
            return
        }
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            val payload = hashMapOf<String, Any>("targetUid" to targetUid, "amount" to amount)
            functions(context)?.getHttpsCallable("transferCoins")?.call(payload)
                ?.addOnSuccessListener { result -> onDone(runCatching { parseTransfer(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun getPortfolio(context: Context, onDone: (Result<Portfolio>) -> Unit) {
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("getPortfolio")?.call()
                ?.addOnSuccessListener { result -> onDone(runCatching { parsePortfolio(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun getMarket(context: Context, onDone: (Result<List<MarketQuote>>) -> Unit) {
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("getVirtualMarket")?.call()
                ?.addOnSuccessListener { result -> onDone(runCatching { parseMarket(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun syncMabarRoom(context: Context, roomCode: String, onDone: (Result<Boolean>) -> Unit = {}) {
        val code = roomCode.trim().uppercase()
        if (code.length < 6) {
            onDone(Result.failure(IllegalArgumentException("Kode room tidak valid")))
            return
        }
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("syncMabarRoom")?.call(hashMapOf("roomCode" to code))
                ?.addOnSuccessListener { onDone(Result.success(true)) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun submitMabarMove(context: Context, roomCode: String, index: Int, onDone: (Result<MabarMove>) -> Unit) {
        val code = roomCode.trim().uppercase()
        if (code.length < 6 || index !in 0..8) {
            onDone(Result.failure(IllegalArgumentException("Move tidak valid")))
            return
        }
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            val payload = hashMapOf<String, Any>("roomCode" to code, "index" to index)
            functions(context)?.getHttpsCallable("submitMabarMove")?.call(payload)
                ?.addOnSuccessListener { result -> onDone(runCatching { parseMabarMove(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun claimMabarReward(context: Context, roomCode: String, onDone: (Result<MabarReward>) -> Unit) {
        val code = roomCode.trim().uppercase()
        if (code.length < 6) {
            onDone(Result.failure(IllegalArgumentException("Kode room tidak valid")))
            return
        }
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            functions(context)?.getHttpsCallable("claimMabarReward")?.call(hashMapOf("roomCode" to code))
                ?.addOnSuccessListener { result -> onDone(runCatching { parseMabarReward(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    fun trade(context: Context, symbol: String, side: String, shares: Int, onDone: (Result<TradeResult>) -> Unit) {
        if (shares <= 0) {
            onDone(Result.failure(IllegalArgumentException("Jumlah lembar harus lebih dari 0")))
            return
        }
        ensureAuth(context) { ok ->
            if (!ok) {
                onDone(Result.failure(IllegalStateException("Firebase Auth belum tersedia")))
                return@ensureAuth
            }
            val payload = hashMapOf<String, Any>(
                "symbol" to symbol,
                "side" to side,
                "shares" to shares
            )
            functions(context)?.getHttpsCallable("tradeVirtualStock")?.call(payload)
                ?.addOnSuccessListener { result -> onDone(runCatching { parseTrade(result.data) }) }
                ?.addOnFailureListener { onDone(Result.failure(it)) }
                ?: onDone(Result.failure(IllegalStateException("Firebase Functions belum tersedia")))
        }
    }

    private fun parseWallet(resultData: Any?): Wallet {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return Wallet(
            coins = number(map["coins"]),
            streak = number(map["streak"]).toInt()
        )
    }

    private fun parseMarket(resultData: Any?): List<MarketQuote> {
        val map = resultData as? Map<*, *> ?: return emptyList()
        val raw = map["quotes"] as? List<*> ?: return emptyList()
        return raw.mapNotNull { entry ->
            val item = entry as? Map<*, *> ?: return@mapNotNull null
            MarketQuote(
                symbol = item["symbol"]?.toString().orEmpty(),
                name = item["name"]?.toString().orEmpty(),
                sector = item["sector"]?.toString().orEmpty(),
                price = number(item["price"]),
                changePct = numberDouble(item["changePct"])
            )
        }.filter { it.symbol.isNotBlank() && it.price > 0L }
    }

    private fun parsePortfolio(resultData: Any?): Portfolio {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val raw = map["holdings"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val holdings = raw.mapNotNull { (key, value) ->
            val symbol = key?.toString().orEmpty()
            val item = value as? Map<*, *> ?: return@mapNotNull null
            val shares = number(item["shares"]).toInt()
            val avg = numberDouble(item["avgCost"])
            if (symbol.isBlank() || shares <= 0) null else symbol to PortfolioHolding(shares, avg)
        }.toMap()
        return Portfolio(number(map["coins"]), holdings)
    }

    private fun parseMabarMove(resultData: Any?): MabarMove {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return MabarMove(
            board = map["board"]?.toString().orEmpty(),
            status = map["status"]?.toString().orEmpty(),
            turn = map["turn"]?.toString().orEmpty()
        )
    }

    private fun parseMabarReward(resultData: Any?): MabarReward {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return MabarReward(
            coins = number(map["coins"]),
            reward = number(map["reward"]),
            role = map["role"]?.toString().orEmpty()
        )
    }

    private fun parseTrade(resultData: Any?): TradeResult {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return TradeResult(
            coins = number(map["coins"]),
            shares = number(map["shares"]).toInt(),
            avgCost = numberDouble(map["avgCost"]),
            price = number(map["price"])
        )
    }

    private fun parseTransfer(resultData: Any?): TransferResult {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return TransferResult(number(map["senderCoins"]), number(map["amount"]))
    }

    private fun parseDaily(resultData: Any?): DailyResult {
        val map = resultData as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return DailyResult(
            coins = number(map["coins"]),
            reward = number(map["reward"]),
            streak = number(map["streak"]).toInt()
        )
    }

    private fun number(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun numberDouble(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}
