package com.xxx.carelorie.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RemoteMacroIntake(
    val userId: String,
    val date: String, // ISO 8601 string
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

@Serializable
data class RemoteFoodLog(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Int? = null,
    val userId: String = "",
    val mealType: String = "",
    val foodName: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val createdAt: String = "",
    /**
     * Local Room key. Marked @Transient so it is never sent to Supabase — that table has no
     * such column. It lets the UI address an entry that has not been given a server id yet.
     */
    @Transient val localId: String = ""
)

@Serializable
data class RemoteFoodPreset(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Int? = null,
    val userId: String? = null,
    val name: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val imageUrl: String? = null
)

@Serializable
data class RemoteUser(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userId: String? = null,
    val email: String,
    val password: String
)

@Serializable
data class RemoteUserProfile(
    val userId: String,
    val name: String = "",
    val birthday: String? = null,
    val gender: String = "",
    val height: String = "",
    val liftingExperience: String = "",
    val weight: Float? = null
)

@Serializable
data class RemoteWeightRecord(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Long? = null,
    val userId: String,
    val weight: Float,
    val date: String
)
