@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val EXPENSE_TAGS = listOf(
    "日常", "餐饮", "房租", "水电", "电话网络", "交通", "购物", "卡债", "欠债还款", "贷款",
    "保险", "医疗", "娱乐", "教育", "人情", "家用", "投资", "其他",
)

val INCOME_TAGS = listOf(
    "工资", "存款", "奖金", "副业", "投资收益", "利息", "退款", "红包", "借入", "其他",
)

/** Used tags (most used first) + presets, no duplicates. */
fun tagCandidates(entries: List<Entry>, isIn: Boolean): List<String> {
    val used = entries
        .filter { (it.amount >= 0) == isIn && it.tag.isNotBlank() }
        .groupingBy { it.tag }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
    return (used + if (isIn) INCOME_TAGS else EXPENSE_TAGS).distinct()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(tag: String, candidates: List<String>, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = tag,
            onValueChange = { onChange(it.take(20)) },
            label = { Text("分类（输入搜索或点选）") },
            singleLine = true,
            trailingIcon = {
                if (tag.isNotEmpty()) {
                    TextButton(onClick = { onChange("") }) { Text("✕") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val q = tag.trim()
        val shown = if (q.isEmpty()) {
            candidates.take(18)
        } else {
            candidates.filter { it.contains(q, ignoreCase = true) }.take(18)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            for (t in shown) {
                FilterChip(
                    selected = t == q,
                    onClick = { onChange(t) },
                    label = { Text(t, fontSize = 13.sp) },
                )
            }
        }
        if (q.isNotEmpty() && candidates.none { it == q }) {
            Text(
                "将新增分类「$q」，下次可以直接选",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
