package com.bittv.iptv.util

import android.content.Context
import com.bittv.iptv.social.FirebaseIdentity
import com.google.firebase.functions.FirebaseFunctions

/** Firebase bridge for server-authoritative multiplayer and game rewards. */
object GameCloudManager {
    private const val REGION = "asia-southeast2"
    private fun functions(context: Context): FirebaseFunctions? = FirebaseIdentity.app(context)?.let { FirebaseFunctions.getInstance(it, REGION) }
    private fun call(context: Context, name: String, data: Map<String, Any?> = emptyMap(), done: (Result<Any?>)->Unit) {
        FirebaseIdentity.ensureAnonymous(context) { auth ->
            if (auth?.currentUser == null) { done(Result.failure(IllegalStateException("Firebase Auth belum aktif"))); return@ensureAnonymous }
            val callable = functions(context)?.getHttpsCallable(name)
            if (callable == null) { done(Result.failure(IllegalStateException("Firebase Functions belum aktif"))); return@ensureAnonymous }
            callable.call(HashMap(data)).addOnSuccessListener { done(Result.success(it.data)) }.addOnFailureListener { done(Result.failure(it)) }
        }
    }
    data class RoomResult(val roomCode:String,val status:String,val queued:Boolean=false)
    data class RaidResult(val bossEnergy:Int,val round:Int,val status:String)
    data class RewardResult(val coins:Long,val reward:Long,val role:String)
    data class LeaderboardEntry(val displayName:String,val score:Long,val wins:Long)

    fun createRaidRoom(context: Context, difficulty:String="normal", done:(Result<RoomResult>)->Unit) = call(context,"createRaidRoom",mapOf("difficulty" to difficulty, "displayName" to GameProfileManager.get(context).displayName)){ done(it.map(::roomResult)) }
    fun joinRaidRoom(context: Context, roomCode:String, done:(Result<RoomResult>)->Unit) = call(context,"joinRaidRoom",mapOf("roomCode" to roomCode.trim().uppercase(), "displayName" to GameProfileManager.get(context).displayName)){ done(it.map(::roomResult)) }
    fun startRaid(context: Context, roomCode:String, done:(Result<Boolean>)->Unit) = call(context,"startRaidRoom",mapOf("roomCode" to roomCode.trim().uppercase())){ done(it.map { true }) }
    fun actionRaid(context: Context, roomCode:String, action:String, done:(Result<RaidResult>)->Unit) = call(context,"submitRaidAction",mapOf("roomCode" to roomCode.trim().uppercase(),"action" to action)){ done(it.map(::raidResult)) }
    fun claimRaidReward(context: Context, roomCode:String, done:(Result<RewardResult>)->Unit) = call(context,"claimRaidReward",mapOf("roomCode" to roomCode.trim().uppercase())){ done(it.map(::rewardResult)) }
    fun quickMatch(context: Context, done:(Result<RoomResult>)->Unit) = call(context,"findRaidMatch",mapOf("displayName" to GameProfileManager.get(context).displayName)){ done(it.map(::roomResult)) }
    fun getRaidLeaderboard(context: Context, done:(Result<List<LeaderboardEntry>>)->Unit) = call(context,"getRaidLeaderboard"){ result ->
        done(result.map { value ->
            val list = (value as? Map<*, *>)?.get("players") as? List<*> ?: emptyList<Any?>()
            list.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                LeaderboardEntry(map["displayName"]?.toString() ?: "Pemain", (map["score"]?.toString()?.toLongOrNull() ?: 0L), (map["wins"]?.toString()?.toLongOrNull() ?: 0L))
            }
        }) }

    private fun mapValue(value:Any?, key:String, fallback:String=""):String = (value as? Map<*, *>)?.get(key)?.toString() ?: fallback
    private fun mapInt(value:Any?, key:String):Int = mapValue(value,key,"0").toIntOrNull() ?: 0
    private fun mapLong(value:Any?, key:String):Long = mapValue(value,key,"0").toLongOrNull() ?: 0L
    private fun roomResult(v:Any?):RoomResult = RoomResult(mapValue(v,"roomCode"),mapValue(v,"status"),mapValue(v,"queued","false").toBoolean())
    private fun raidResult(v:Any?):RaidResult = RaidResult(mapInt(v,"bossEnergy"),mapInt(v,"round"),mapValue(v,"status"))
    private fun rewardResult(v:Any?):RewardResult = RewardResult(mapLong(v,"coins"),mapLong(v,"reward"),mapValue(v,"role"))
}
