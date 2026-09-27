@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lkcheng.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style scroll wheel: swipe up/down, the middle row is the selected value. */
@Composable
fun WheelPicker(items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelected: (Int) -> Unit) {
    val rowH = 40.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, maxOf(0, items.size - 1)))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val center by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0)
                .coerceIn(0, maxOf(0, items.size - 1))
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (!moving) onSelected(center)
        }
    }
    Box(modifier.height(rowH * 3), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rowH)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowH),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size) { i ->
                Box(Modifier.fillMaxWidth().height(rowH), contentAlignment = Alignment.Center) {
                    Text(
                        items[i],
                        fontSize = if (i == center) 18.sp else 15.sp,
                        fontWeight = if (i == center) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == center) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
