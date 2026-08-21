package com.example.spatialtennis.domain.model

data class CupOpponent(
    val archetype: AiArchetype,
    val roundName: String,
    val targetScore: Int,
    val tip: String,
)

val STAR_CUP_OPPONENTS =
    listOf(
        CupOpponent(AiArchetype.SPEEDSTER, "第一战", 5, "提前向落点移动，别站死中路。"),
        CupOpponent(AiArchetype.TRICKSTER, "第二战", 5, "看到高弧线就向前接球。"),
        CupOpponent(AiArchetype.POWERHOUSE, "决赛", 7, "用吊球和左右调动限制强攻。"),
    )

data class CupProgress(
    val championCount: Int = 0,
    val bestStage: Int = 0,
)
