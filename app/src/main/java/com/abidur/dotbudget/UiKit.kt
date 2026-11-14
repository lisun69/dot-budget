package com.abidur.dotbudget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

val NRed = Color(0xFFD71921)
val NBlack = Color(0xFF000000)
val NCard = Color(0xFF0D0D0D)
val NLine = Color(0xFF262626)
val NGrey = Color(0xFF8A8A8A)
val NWhite = Color(0xFFFFFFFF)
val NGhost = Color(0x22FFFFFF)
val Mono = FontFamily.Monospace

@Composable
fun NothingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NRed, onPrimary = NWhite,
            background = NBlack, onBackground = NWhite,
            surface = NCard, onSurface = NWhite,
            surfaceVariant = NCard, onSurfaceVariant = NGrey,
            outline = NLine
        ),
        content = content
    )
}

@Composable
fun NText(
    text: String,
    size: TextUnit = 13.sp,
    color: Color = NWhite,
    modifier: Modifier = Modifier,
    spacing: TextUnit = 0.sp,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size, fontFamily = Mono,
        letterSpacing = spacing, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align
    )
}

@Composable
fun NLabel(text: String, modifier: Modifier = Modifier) {
    NText(text.uppercase(), 11.sp, NGrey, modifier, spacing = 1.5.sp, maxLines = 1)
}

@Composable
fun RedDot(size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(NRed))
}

@Composable
fun NCardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(NCard).border(1.dp, NLine, shape).padding(18.dp),
        content = content
    )
}

@Composable
fun NButton(text: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (filled) NRed else Color.Transparent)
            .border(1.dp, if (filled) NRed else NLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        NText(text.uppercase(), 12.sp, NWhite, spacing = 1.sp, maxLines = 1)
    }
}

@Composable
fun DotText(text: String, dot: Dp, color: Color, modifier: Modifier = Modifier, ghost: Color = Color.Transparent) {
    val cols = DotFont.columns(text).coerceAtLeast(1)
    Canvas(modifier.size(dot * cols, dot * 7)) {
        val cell = size.height / 7f
        val r = cell * 0.38f
        DotFont.forEachDot(text) { x, y, on ->
            val col = if (on) color else ghost
            if (col.alpha > 0f) drawCircle(col, r, Offset(x * cell + cell / 2f, y * cell + cell / 2f))
        }
    }
}

@Composable
fun BigDot(text: String, color: Color, maxDot: Dp = 9.dp, modifier: Modifier = Modifier, ghost: Color = NGhost) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = DotFont.columns(text).coerceAtLeast(1)
        val fit = maxWidth / cols
        val dot = if (fit < maxDot) fit else maxDot
        DotText(text, dot, color, ghost = ghost)
    }
}

@Composable
fun DotProgress(fraction: Float, modifier: Modifier = Modifier, count: Int = 24, lit: Color = NWhite, ghost: Color = NGhost) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val cell = size.width / count
        val r = minOf(cell, size.height) * 0.38f
        val on = (fraction.coerceIn(0f, 1f) * count).roundToInt()
        for (i in 0 until count) {
            drawCircle(if (i < on) lit else ghost, r, Offset(cell * i + cell / 2f, size.height / 2f))
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NRed,
    unfocusedBorderColor = NLine,
    focusedLabelColor = NRed,
    unfocusedLabelColor = NGrey,
    cursorColor = NRed,
    focusedTextColor = NWhite,
    unfocusedTextColor = NWhite,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

val fieldText = TextStyle(fontFamily = Mono, color = NWhite, fontSize = 16.sp)

enum class EntryMode { SPEND, SALARY, EXTRA }

@Composable
fun EntryCard(mode: EntryMode, suggestions: List<String>, onSave: (String, Double) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val title = when (mode) {
        EntryMode.SPEND -> "ADD SPEND"
        EntryMode.SALARY -> "NEW SALARY"
        EntryMode.EXTRA -> "ADD EXTRA MONEY"
    }

    fun save() {
        val amt = amount.toDoubleOrNull()
        when {
            mode == EntryMode.SPEND && name.isBlank() -> error = "Enter where you spent (e.g. Fuel)"
            amt == null || amt <= 0.0 -> error = "Enter an amount in TK"
            else -> onSave(if (mode == EntryMode.SALARY) "Salary" else name.ifBlank { "Extra" }, amt ?: 0.0)
        }
    }

    NCardBox(Modifier.widthIn(max = 440.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedDot()
            Spacer(Modifier.width(10.dp))
            NText(title, 14.sp, spacing = 2.sp)
        }
        if (mode == EntryMode.SALARY) {
            Spacer(Modifier.height(8.dp))
            NText("This starts a new month. Enter the total money you have.", 12.sp, NGrey)
        }
        Spacer(Modifier.height(16.dp))

        if (mode != EntryMode.SALARY) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { NText(if (mode == EntryMode.SPEND) "Event (Date, Home, Fuel...)" else "Source (Bonus, Gift...)", 12.sp) },
                singleLine = true,
                textStyle = fieldText,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            if (mode == EntryMode.SPEND && suggestions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        val chip = RoundedCornerShape(50)
                        Box(
                            Modifier.clip(chip)
                                .background(if (Fmt.key(s) == Fmt.key(name)) NRed else Color.Transparent)
                                .border(1.dp, NLine, chip)
                                .clickable { name = s; error = null }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { NText(s, 12.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }; error = null },
            label = { NText("Amount (TK / BDT)", 12.sp) },
            singleLine = true,
            textStyle = fieldText,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = if (mode == EntryMode.SALARY) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            NText(it, 12.sp, NRed)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            NButton("Save", filled = true, modifier = Modifier.weight(1f)) { save() }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Simple form pop-up used for bank accounts and cards. */
@Composable
fun FormDialog(
    title: String,
    labels: List<String>,
    initial: List<String>,
    numberField: Int,
    validate: (List<String>) -> String?,
    onSave: (List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit
) {
    val values = remember { mutableStateListOf(*initial.toTypedArray()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NCardBox(Modifier.widthIn(max = 440.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedDot()
                    Spacer(Modifier.width(10.dp))
                    NText(title, 14.sp, spacing = 2.sp)
                }
                Spacer(Modifier.height(16.dp))
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it; error = null },
                        label = { NText(l, 12.sp) },
                        singleLine = true,
                        textStyle = fieldText,
                        colors = fieldColors(),
                        keyboardOptions = if (i == numberField) {
                            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        } else {
                            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                error?.let {
                    NText(it, 12.sp, NRed)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                    NButton("Save", filled = true, modifier = Modifier.weight(1f)) {
                        val v = values.map { it.trim() }
                        val problem = if (v.any { it.isBlank() }) "Please fill in all fields" else validate(v)
                        if (problem != null) error = problem else onSave(v)
                    }
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(10.dp))
                    NButton(if (confirmDelete) "Tap again to delete" else "Delete", modifier = Modifier.fillMaxWidth()) {
                        if (confirmDelete) onDelete() else confirmDelete = true
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseRow(e: Expense, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            NText(Fmt.title(e.name), 14.sp, maxLines = 1)
            NText(Fmt.dateTime(e.time), 11.sp, NGrey, maxLines = 1)
        }
        NText("-" + Fmt.money(e.amount), 14.sp)
    }
}

@Composable
fun CategoryRow(name: String, amount: Double, total: Double, lit: Color = NWhite) {
    val frac = if (total > 0) (amount / total).toFloat() else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NText(name, 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
            NText("${(frac * 100).roundToInt()}%  ", 11.sp, NGrey)
            NText(Fmt.money(amount), 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        DotProgress(frac, count = 30, lit = lit)
    }
}

/** One label + value line inside an account card. */
@Composable
fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        NText(label.uppercase(), 10.sp, NGrey, spacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        NText(value, 15.sp)
    }
}
