package com.abidur.dotbudget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NothingTheme { BudgetApp(tick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue = tick.intValue + 1 // reload after adding from the widget
    }
}

@Composable
fun BudgetApp(tick: Int) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val key = tick + version * 100000
    val snap = remember(key) { Store.load(ctx) }
    val name = remember(key) { Profile.name(ctx) }
    val changed: () -> Unit = { version += 1 }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<EntryMode?>(null) }

    Box(Modifier.fillMaxSize().background(NBlack)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(name)
            Tabs(tab) { tab = it }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeTab(snap, name, onSalary = { dialog = EntryMode.SALARY }, onExtra = { dialog = EntryMode.EXTRA }, onSetName = { tab = 4 })
                    1 -> StatsTab(snap)
                    2 -> HistoryTab(snap, changed)
                    3 -> AccountsTab(key, changed)
                    else -> SetupTab(snap, key, changed)
                }
            }
        }
        if (tab <= 2) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(24.dp).size(64.dp).clip(CircleShape)
                    .background(NRed).clickable { dialog = EntryMode.SPEND },
                contentAlignment = Alignment.Center
            ) { DotText("+", 4.dp, NWhite) }
        }

        dialog?.let { mode ->
            Dialog(onDismissRequest = { dialog = null }) {
                EntryCard(
                    mode = mode,
                    suggestions = snap.suggestions(),
                    onSave = { n, amt ->
                        when (mode) {
                            EntryMode.SPEND -> Store.addExpense(ctx, n, amt)
                            EntryMode.SALARY -> Store.addSalary(ctx, amt, false, "Salary")
                            EntryMode.EXTRA -> Store.addSalary(ctx, amt, true, n)
                        }
                        dialog = null
                        changed()
                    },
                    onCancel = { dialog = null }
                )
            }
        }
    }
}

@Composable
fun TopBar(name: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotText("BUDGET", 3.dp, NWhite)
        Spacer(Modifier.width(8.dp))
        RedDot(7.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            if (name.isNotBlank()) NText(name.uppercase(), 12.sp, NWhite, spacing = 1.sp, maxLines = 1)
            NText(Fmt.today(), 10.sp, NGrey, spacing = 1.sp, maxLines = 1)
        }
    }
}

@Composable
fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val names = listOf("HOME", "STATS", "HISTORY", "ACCOUNTS", "SETUP")
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        names.forEachIndexed { i, n ->
            Column(
                Modifier.weight(if (n.length > 6) 1.25f else 1f).clickable { onSelect(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NText(n, 11.sp, if (i == selected) NWhite else NGrey, spacing = 0.5.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == selected) NRed else Color.Transparent))
            }
        }
    }
}

@Composable
fun Stat(title: String, value: String, modifier: Modifier = Modifier, color: Color = NWhite) {
    Column(modifier) {
        NLabel(title)
        Spacer(Modifier.height(4.dp))
        NText(value, 15.sp, color, maxLines = 1)
    }
}

// ------------------------------------------------------------ HOME
@Composable
fun HomeTab(s: Snapshot, name: String, onSalary: () -> Unit, onExtra: () -> Unit, onSetName: () -> Unit) {
    val c = s.current
    val frac = if (c.income > 0) (c.left / c.income).toFloat() else 0f
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (name.isBlank()) {
            NCardBox {
                NText("Add your name so this budget is yours.", 13.sp, NGrey)
                Spacer(Modifier.height(12.dp))
                NButton("Set your name", modifier = Modifier.fillMaxWidth()) { onSetName() }
            }
        }

        NCardBox {
            NLabel(if (c.hasSalary) "TK left since ${Fmt.day(c.start)}" else "TK left")
            Spacer(Modifier.height(14.dp))
            BigDot(Fmt.money(c.left), if (c.left < 0) NRed else NWhite, 9.dp)
            Spacer(Modifier.height(10.dp))
            NText("of ${Fmt.money(c.income)} TK", 12.sp, NGrey)
            Spacer(Modifier.height(16.dp))
            DotProgress(frac, lit = if (frac < 0.2f) NRed else NWhite)
            Spacer(Modifier.height(16.dp))
            Row {
                Stat("Spent", Fmt.money(c.spent), Modifier.weight(1f))
                Stat("Saved", if (c.income > 0) "${(frac * 100).roundToInt()}%" else "-", Modifier.weight(1f))
                val d = Fmt.daysToPayday(s.payday)
                Stat("Payday", if (d == 0) "TODAY" else "${d}D", Modifier.weight(1f), if (d <= 3) NRed else NWhite)
            }
        }

        if (!c.hasSalary) {
            NCardBox {
                NText("Start by adding your salary. Your month runs from salary day to the next salary.", 13.sp, NGrey)
                Spacer(Modifier.height(14.dp))
                NButton("Add salary", filled = true, modifier = Modifier.fillMaxWidth()) { onSalary() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("New salary", modifier = Modifier.weight(1f)) { onSalary() }
                NButton("Add extra", modifier = Modifier.weight(1f)) { onExtra() }
            }
        }

        val cats = categories(c.expenses)
        if (cats.isNotEmpty()) {
            NCardBox {
                NLabel("Where it went")
                Spacer(Modifier.height(6.dp))
                cats.take(5).forEachIndexed { i, (n, a) -> CategoryRow(n, a, c.spent, if (i == 0) NRed else NWhite) }
            }
        }

        NCardBox {
            NLabel("Recent")
            Spacer(Modifier.height(6.dp))
            if (c.expenses.isEmpty()) NText("No spends yet. Tap + to add one.", 13.sp, NGrey)
            else c.expenses.take(6).forEach { ExpenseRow(it) }
        }
    }
}

// ------------------------------------------------------------ STATS
@Composable
fun StatsTab(s: Snapshot) {
    var allTime by rememberSaveable { mutableStateOf(false) }
    val exps = if (allTime) s.expenses else s.current.expenses
    val total = exps.sumOf { it.amount }
    val cats = categories(exps)
    val months = s.cycles.filter { it.hasSalary }.takeLast(6)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton("This month", filled = !allTime, modifier = Modifier.weight(1f)) { allTime = false }
            NButton("All time", filled = allTime, modifier = Modifier.weight(1f)) { allTime = true }
        }

        NCardBox {
            NLabel(if (allTime) "All-time spending (TK)" else "Spent this month (TK)")
            Spacer(Modifier.height(14.dp))
            BigDot(Fmt.money(total), NWhite, 7.dp)
            Spacer(Modifier.height(10.dp))
            NText("${exps.size} entries / ${cats.size} events", 12.sp, NGrey)
        }

        NCardBox {
            NLabel("By event")
            Spacer(Modifier.height(6.dp))
            if (cats.isEmpty()) NText("No spends yet.", 13.sp, NGrey)
            else cats.forEachIndexed { i, (n, a) -> CategoryRow(n, a, total, if (i == 0) NRed else NWhite) }
        }

        NCardBox {
            NLabel("Month by month")
            if (months.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                NText("Add your salary to see monthly comparison.", 13.sp, NGrey)
            } else {
                MonthChart(months)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedDot(7.dp); NText("  SPENT    ", 11.sp, NGrey)
                    Box(Modifier.size(7.dp).clip(CircleShape).background(NWhite)); NText("  SAVED", 11.sp, NGrey)
                }
                Spacer(Modifier.height(10.dp))
                months.reversed().forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NText(m.range, 12.sp, maxLines = 1)
                            NText("IN ${Fmt.money(m.income)}", 11.sp, NGrey, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            NText("-${Fmt.money(m.spent)}", 13.sp, NRed)
                            NText("SAVED ${Fmt.money(m.left)}", 11.sp, if (m.left < 0) NRed else NWhite)
                        }
                    }
                }
            }
        }

        val inAll = s.salaries.sumOf { it.amount }
        val outAll = s.expenses.sumOf { it.amount }
        NCardBox {
            NLabel("Lifetime")
            Spacer(Modifier.height(12.dp))
            Row {
                Stat("Money in", Fmt.money(inAll), Modifier.weight(1f))
                Stat("Spent", Fmt.money(outAll), Modifier.weight(1f))
                Stat("Saved", Fmt.money(inAll - outAll), Modifier.weight(1f), if (inAll - outAll < 0) NRed else NWhite)
            }
        }
    }
}

@Composable
fun MonthChart(months: List<Cycle>) {
    val max = months.maxOf { maxOf(it.income, it.spent) }.coerceAtLeast(1.0)
    val rows = 14
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        months.forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(28.dp, 126.dp)) {
                    val cell = size.height / rows
                    val r = cell * 0.36f
                    val spentN = (m.spent / max * rows).roundToInt().coerceIn(0, rows)
                    val savedN = (m.left.coerceAtLeast(0.0) / max * rows).roundToInt().coerceIn(0, rows)
                    for (i in 0 until rows) {
                        val y = size.height - cell * i - cell / 2f
                        drawCircle(if (i < spentN) NRed else NGhost, r, Offset(size.width * 0.27f, y))
                        drawCircle(if (i < savedN) NWhite else NGhost, r, Offset(size.width * 0.73f, y))
                    }
                }
                Spacer(Modifier.height(6.dp))
                NText(m.month, 10.sp, NGrey)
            }
        }
    }
}

// ------------------------------------------------------------ HISTORY
@Composable
fun HistoryTab(s: Snapshot, changed: () -> Unit) {
    val ctx = LocalContext.current
    var toDelete by remember { mutableStateOf<Any?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
        if (s.expenses.isEmpty() && s.salaries.isEmpty()) {
            item { NText("Nothing here yet. Tap + to add a spend.", 13.sp, NGrey) }
        }
        s.cycles.reversed().forEach { c ->
            item {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RedDot(6.dp)
                        Spacer(Modifier.width(8.dp))
                        NLabel(c.range)
                    }
                    Spacer(Modifier.height(4.dp))
                    NText("IN ${Fmt.money(c.income)}  /  OUT ${Fmt.money(c.spent)}  /  LEFT ${Fmt.money(c.left)}", 11.sp, NGrey)
                }
            }
            items(c.incomes) { inc ->
                Row(Modifier.fillMaxWidth().clickable { toDelete = inc }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NText(if (inc.extra) "+ ${inc.note.ifBlank { "Extra" }}" else "+ Salary", 14.sp)
                        NText(Fmt.dateTime(inc.time), 11.sp, NGrey)
                    }
                    NText("+" + Fmt.money(inc.amount), 14.sp)
                }
            }
            items(c.expenses) { e -> ExpenseRow(e) { toDelete = e } }
        }
        item {
            Spacer(Modifier.height(12.dp))
            NText("Tap any entry to delete it.", 11.sp, NGrey)
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = NCard,
            title = { NText("DELETE ENTRY?", 15.sp, spacing = 1.sp) },
            text = {
                val txt = when (item) {
                    is Expense -> "${Fmt.title(item.name)}  ${Fmt.money(item.amount)} TK"
                    is Salary -> "${if (item.extra) item.note else "Salary"}  ${Fmt.money(item.amount)} TK"
                    else -> ""
                }
                NText(txt, 13.sp, NGrey)
            },
            confirmButton = {
                NButton("Delete", filled = true) {
                    when (item) {
                        is Expense -> Store.deleteExpense(ctx, item.id)
                        is Salary -> Store.deleteSalary(ctx, item.id)
                    }
                    toDelete = null
                    changed()
                }
            },
            dismissButton = { NButton("Keep") { toDelete = null } }
        )
    }
}

// ------------------------------------------------------------ ACCOUNTS (bank + card details)
@Composable
fun AccountsTab(key: Int, changed: () -> Unit) {
    val ctx = LocalContext.current
    val banks = remember(key) { Wallet.banks(ctx) }
    val cards = remember(key) { Wallet.cards(ctx) }
    var editBank by remember { mutableStateOf<Bank?>(null) }
    var editCard by remember { mutableStateOf<CardInfo?>(null) }
    var shown by remember { mutableStateOf(setOf<Long>()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NCardBox {
            NLabel("Receive money")
            Spacer(Modifier.height(8.dp))
            NText("Save your bank and card details. Share them in one tap when someone needs to send you money.", 12.sp, NGrey)
            if (banks.isNotEmpty() || cards.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                NButton("Share all details", filled = true, modifier = Modifier.fillMaxWidth()) { Wallet.share(ctx, Wallet.allText(ctx)) }
            }
        }

        Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RedDot(6.dp)
            Spacer(Modifier.width(8.dp))
            NLabel("Bank accounts")
        }
        banks.forEachIndexed { i, b ->
            NCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NText(String.format("BANK %02d", i + 1), 13.sp, NRed, Modifier.weight(1f), spacing = 1.sp)
                    NText("EDIT", 11.sp, NGrey, Modifier.clickable { editBank = b }.padding(6.dp), spacing = 1.sp)
                }
                Spacer(Modifier.height(6.dp))
                InfoLine("Bank name", b.bank)
                InfoLine("Account number", b.account)
                InfoLine("Branch", b.branch)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton("Copy", modifier = Modifier.weight(1f)) { Wallet.copy(ctx, Wallet.bankText(ctx, b)) }
                    NButton("Share", modifier = Modifier.weight(1f)) { Wallet.share(ctx, Wallet.bankText(ctx, b)) }
                }
            }
        }
        NButton("+ Add bank account", modifier = Modifier.fillMaxWidth()) { editBank = Bank(0L, "", "", "") }

        Row(Modifier.padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RedDot(6.dp)
            Spacer(Modifier.width(8.dp))
            NLabel("Cards")
        }
        cards.forEachIndexed { i, card ->
            val visible = card.id in shown
            NCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NText(String.format("CARD %02d", i + 1), 13.sp, NRed, Modifier.weight(1f), spacing = 1.sp)
                    NText("EDIT", 11.sp, NGrey, Modifier.clickable { editCard = card }.padding(6.dp), spacing = 1.sp)
                }
                Spacer(Modifier.height(6.dp))
                InfoLine("Card holder name", card.holder)
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        InfoLine("Card number", if (visible) Wallet.groupCard(card.number) else Wallet.maskCard(card.number))
                    }
                    NText(
                        if (visible) "HIDE" else "SHOW", 11.sp, NGrey,
                        Modifier.clickable { shown = if (visible) shown - card.id else shown + card.id }.padding(6.dp),
                        spacing = 1.sp
                    )
                }
                InfoLine("Bank name", card.bank)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton("Copy", modifier = Modifier.weight(1f)) { Wallet.copy(ctx, Wallet.cardText(card)) }
                    NButton("Share", modifier = Modifier.weight(1f)) { Wallet.share(ctx, Wallet.cardText(card)) }
                }
            }
        }
        NButton("+ Add card", modifier = Modifier.fillMaxWidth()) { editCard = CardInfo(0L, Profile.name(ctx), "", "") }

        Spacer(Modifier.height(6.dp))
        NText("Saved only on this phone. Never share your card PIN, CVV, expiry date or OTP.", 11.sp, NGrey)
    }

    editBank?.let { b ->
        FormDialog(
            title = if (b.id == 0L) "ADD BANK ACCOUNT" else "EDIT BANK ACCOUNT",
            labels = listOf("Bank name", "Account number", "Branch"),
            initial = listOf(b.bank, b.account, b.branch),
            numberField = 1,
            validate = { null },
            onSave = { v ->
                Wallet.saveBank(ctx, b.copy(bank = v[0], account = v[1], branch = v[2]))
                editBank = null
                changed()
            },
            onDelete = if (b.id == 0L) null else ({ Wallet.deleteBank(ctx, b.id); editBank = null; changed() }),
            onCancel = { editBank = null }
        )
    }

    editCard?.let { card ->
        FormDialog(
            title = if (card.id == 0L) "ADD CARD" else "EDIT CARD",
            labels = listOf("Card holder name", "Card number", "Bank name"),
            initial = listOf(card.holder, Wallet.groupCard(card.number), card.bank),
            numberField = 1,
            validate = { v ->
                val digits = v[1].filter { it.isDigit() }
                if (digits.length !in 12..19) "Card number should be 12 to 19 digits" else null
            },
            onSave = { v ->
                Wallet.saveCard(ctx, card.copy(holder = v[0], number = v[1].filter { it.isDigit() }, bank = v[2]))
                editCard = null
                changed()
            },
            onDelete = if (card.id == 0L) null else ({ Wallet.deleteCard(ctx, card.id); editCard = null; changed() }),
            onCancel = { editCard = null }
        )
    }
}

// ------------------------------------------------------------ SETUP
@Composable
fun SetupTab(s: Snapshot, key: Int, changed: () -> Unit) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var fullName by remember(key) { mutableStateOf(Profile.name(ctx)) }
    var nick by remember(key) { mutableStateOf(Profile.nick(ctx)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson(ctx).toByteArray()) }
            }.isSuccess
            Toast.makeText(ctx, if (ok) "Backup saved" else "Could not save backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
            val ok = text != null && Store.importJson(ctx, text)
            Toast.makeText(ctx, if (ok) "Backup restored" else "Not a valid backup file", Toast.LENGTH_SHORT).show()
            if (ok) changed()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NCardBox {
            NLabel("Your name")
            Spacer(Modifier.height(8.dp))
            NText("Full name shows in the app. Nickname shows small on the widget.", 11.sp, NGrey)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { NText("Full name (e.g. Lisun Rahman)", 12.sp) },
                singleLine = true,
                textStyle = fieldText,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it.take(12) },
                label = { NText("Nickname (e.g. Lisun)", 12.sp) },
                singleLine = true,
                textStyle = fieldText,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            NButton("Save name", filled = true, modifier = Modifier.fillMaxWidth()) {
                Profile.save(ctx, fullName, nick)
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                changed()
            }
        }

        NCardBox {
            NLabel("Home screen widget")
            Spacer(Modifier.height(8.dp))
            NText("Theme: ${WidgetLook.style(ctx).label}  ·  Opacity: ${WidgetLook.opacity(ctx)}%", 12.sp)
            Spacer(Modifier.height(12.dp))
            NButton("Widget theme & opacity", filled = true, modifier = Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent(ctx, WidgetSettingsActivity::class.java))
            }
            Spacer(Modifier.height(10.dp))
            NText(
                "Add: long-press home screen > Widgets > Dot Budget. Resize: long-press the widget and drag the edges. Tap the red + to add a spend. Tap the small dot-gear to change the look.",
                11.sp, NGrey
            )
        }

        NCardBox {
            NLabel("Salary day")
            Spacer(Modifier.height(8.dp))
            NText("Used for the payday countdown. Your month resets when you tap NEW SALARY.", 12.sp, NGrey)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NButton("-") { Store.setPayday(ctx, s.payday - 1); changed() }
                Spacer(Modifier.weight(1f))
                DotText(s.payday.toString(), 6.dp, NWhite, ghost = NGhost)
                Spacer(Modifier.weight(1f))
                NButton("+") { Store.setPayday(ctx, s.payday + 1); changed() }
            }
        }

        NCardBox {
            NLabel("Backup")
            Spacer(Modifier.height(8.dp))
            NText("Saves money records, your name and account details. Also opens backups from the old Nothing Budget app.", 12.sp, NGrey)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("Export", modifier = Modifier.weight(1f)) { exportLauncher.launch("dot-budget-backup.json") }
                NButton("Import", modifier = Modifier.weight(1f)) { importLauncher.launch(arrayOf("application/json", "*/*")) }
            }
        }

        NCardBox {
            NLabel("Danger zone")
            Spacer(Modifier.height(12.dp))
            NButton("Clear all data", modifier = Modifier.fillMaxWidth()) { confirmClear = true }
        }

        // About (small)
        val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedDot(5.dp)
                Spacer(Modifier.width(6.dp))
                NText("DOT BUDGET  v$ver", 10.sp, NGrey, spacing = 1.sp)
            }
            Spacer(Modifier.height(4.dp))
            NText("Developed by Lisun", 10.sp, NGrey)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = NCard,
            title = { NText("CLEAR EVERYTHING?", 15.sp, spacing = 1.sp) },
            text = { NText("All salaries, spends, your name and saved bank/card details will be deleted. This cannot be undone.", 13.sp, NGrey) },
            confirmButton = { NButton("Clear", filled = true) { Store.clear(ctx); confirmClear = false; changed() } },
            dismissButton = { NButton("Cancel") { confirmClear = false } }
        )
    }
}
