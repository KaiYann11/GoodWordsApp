package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.graphics.Color
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.ui.theme.Amber
import com.codex.appgoodwords.ui.theme.AmberSoft
import com.codex.appgoodwords.ui.theme.Indigo
import com.codex.appgoodwords.ui.theme.IndigoSoft
import com.codex.appgoodwords.ui.theme.Mint
import com.codex.appgoodwords.ui.theme.MintSoft
import com.codex.appgoodwords.ui.theme.Rose
import com.codex.appgoodwords.ui.theme.RoseSoft
import com.codex.appgoodwords.ui.theme.Violet
import com.codex.appgoodwords.ui.theme.VioletSoft

/**
 * 걸음마다 다른 색.
 *
 * 목록이 한 가지 색이면 줄이 다 같아 보여서, 어디까지 했는지 세어 봐야 압니다.
 * 색이 다르면 세지 않아도 눈이 먼저 압니다.
 *
 * **색은 표시일 뿐이고 뜻을 지지 않습니다.** 빨강이 "잘못한 것"이 되면 안 되기 때문입니다.
 * 이 앱은 못 한 것을 붉게 세지 않습니다 — 색은 걸음을 가리기만 합니다.
 */
data class StepColors(val accent: Color, val soft: Color)

fun stepColors(step: DailyStep): StepColors = when (step) {
    DailyStep.QUOTE -> StepColors(Indigo, IndigoSoft)
    DailyStep.ROUTINE -> StepColors(Mint, MintSoft)
    DailyStep.TODO -> StepColors(Amber, AmberSoft)
    DailyStep.READING -> StepColors(Violet, VioletSoft)
    DailyStep.DIARY -> StepColors(Rose, RoseSoft)
}
