package com.abidur.dotbudget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class Expense(val id: Long, val name: String, val amount: Double, val time: Long)

/** Money in. extra = false means "salary" and starts a new month. */
data class Salary(val id: Long, val amount: Double, val time: Long, val extra: Boolean, val note: String)

/** Bank account details for receiving money. */
data class Bank(val id: Long, val bank: String, val account: String, val branch: String)

/** Card details for receiving money. Number is stored as digits only. No CVV, PIN or expiry. */
data class CardInfo(val id: Long, val holder: String, val number: String, val bank: String)

class Cycle(
    val start: Long,
    val end: Long,
    val incomes: List<Salary>,
    val expenses: List<Expense>,
    val hasSalary: Boolean
) {
    val income: Double = incomes.sumOf { it.amount }
    val spent: Double = expenses.sumOf { it.amount }
    val left: Double get() = income - spent
    val month: String get() = if (hasSalary) Fmt.month(start) else "START"
    val range: String
        get() = when {
            !hasSalary -> "BEFORE FIRST SALARY"
            end == Long.MAX_VALUE -> "${Fmt.day(start)} -> NOW"
            else -> "${Fmt.day(start)} -> ${Fmt.day(end)}"
        }
}

class Snapshot(val expenses: List<Expense>, val salaries: List<Salary>, val payday: Int) {

    val cycles: List<Cycle> by lazy { buildCycles() }
    val current: Cycle get() = cycles.last()

    private fun buildCycles(): List<Cycle> {
        val starts = salaries.filter { !it.extra }.sortedBy { it.time }
        val result = mutableListOf<Cycle>()
        val firstStart = starts.firstOrNull()?.time ?: Long.MAX_VALUE
        val preEx = expenses.filter { it.time < firstStart }.sortedByDescending { it.time }
        val preIn = salaries.filter { it.extra && it.time < firstStart }
        if (starts.isEmpty() || preEx.isNotEmpty() || preIn.isNotEmpty()) {
            result += Cycle(0L, firstStart, preIn, preEx, false)
        }
        starts.forEachIndexed { i, s ->
            val end = if (i + 1 < starts.size) starts[i + 1].time else Long.MAX_VALUE
            val ins = salaries.filter { it.time >= s.time && it.time < end && (it.extra || it.id == s.id) }
            val ex = expenses.filter { it.time >= s.time && it.time < end }.sortedByDescending { it.time }
            result += Cycle(s.time, end, ins, ex, true)
        }
        return result
    }

    fun suggestions(): List<String> =
        expenses.groupBy { Fmt.key(it.name) }
            .entries.sortedByDescending { it.value.size }
            .map { Fmt.title(it.key) }
            .take(10)
}

fun categories(list: List<Expense>): List<Pair<String, Double>> =
    list.groupBy { Fmt.key(it.name) }
        .map { (k, v) -> Fmt.title(k) to v.sumOf { it.amount } }
        .sortedByDescending { it.second }

private fun prefs(c: Context) =
    c.applicationContext.getSharedPreferences("nothing_budget", Context.MODE_PRIVATE)

private fun newId(): Long = System.currentTimeMillis() * 1000 + (0..999).random()

object Store {
    private const val K_EXP = "expenses"
    private const val K_SAL = "salaries"
    private const val K_PAY = "payday"

    fun load(c: Context): Snapshot {
        val p = prefs(c)
        return Snapshot(
            parseExpenses(p.getString(K_EXP, "[]") ?: "[]"),
            parseSalaries(p.getString(K_SAL, "[]") ?: "[]"),
            p.getInt(K_PAY, 23)
        )
    }

    fun addExpense(c: Context, name: String, amount: Double) {
        val s = load(c)
        save(c, s.expenses + Expense(newId(), name.trim(), amount, System.currentTimeMillis()), s.salaries)
    }

    fun addSalary(c: Context, amount: Double, extra: Boolean, note: String) {
        val s = load(c)
        save(c, s.expenses, s.salaries + Salary(newId(), amount, System.currentTimeMillis(), extra, note.trim()))
    }

    fun deleteExpense(c: Context, id: Long) {
        val s = load(c)
        save(c, s.expenses.filter { it.id != id }, s.salaries)
    }

    fun deleteSalary(c: Context, id: Long) {
        val s = load(c)
        save(c, s.expenses, s.salaries.filter { it.id != id })
    }

    fun setPayday(c: Context, day: Int) {
        prefs(c).edit().putInt(K_PAY, day.coerceIn(1, 31)).apply()
        BudgetWidget.refresh(c)
    }

    /** Clears money, name and account details. Widget look is kept. */
    fun clear(c: Context) {
        prefs(c).edit().clear().apply()
        BudgetWidget.refresh(c)
    }

    fun exportJson(c: Context): String {
        val s = load(c)
        return JSONObject()
            .put("app", "DotBudget")
            .put("payday", s.payday)
            .put(K_EXP, expensesJson(s.expenses))
            .put(K_SAL, salariesJson(s.salaries))
            .put("name", Profile.name(c))
            .put("nick", Profile.nick(c))
            .put("banks", Wallet.banksJson(Wallet.banks(c)))
            .put("cards", Wallet.cardsJson(Wallet.cards(c)))
            .toString(2)
    }

    /** Works with backups from the old Nothing Budget app too. */
    fun importJson(c: Context, text: String): Boolean = try {
        val o = JSONObject(text)
        val ex = parseExpenses(o.getJSONArray(K_EXP).toString())
        val sa = parseSalaries(o.getJSONArray(K_SAL).toString())
        val e = prefs(c).edit().putInt(K_PAY, o.optInt("payday", 23))
        if (o.has("name")) e.putString("p_name", o.optString("name", ""))
        if (o.has("nick")) e.putString("p_nick", o.optString("nick", ""))
        o.optJSONArray("banks")?.let { e.putString("banks", it.toString()) }
        o.optJSONArray("cards")?.let { e.putString("cards", it.toString()) }
        e.apply()
        save(c, ex, sa)
        true
    } catch (e: Exception) {
        false
    }

    private fun save(c: Context, exps: List<Expense>, sals: List<Salary>) {
        prefs(c).edit()
            .putString(K_EXP, expensesJson(exps).toString())
            .putString(K_SAL, salariesJson(sals).toString())
            .apply()
        BudgetWidget.refresh(c)
    }

    private fun parseExpenses(s: String): List<Expense> = try {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Expense(o.getLong("id"), o.getString("name"), o.getDouble("amount"), o.getLong("time"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseSalaries(s: String): List<Salary> = try {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Salary(o.getLong("id"), o.getDouble("amount"), o.getLong("time"), o.optBoolean("extra", false), o.optString("note", ""))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun expensesJson(list: List<Expense>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("time", it.time)) }
    }

    private fun salariesJson(list: List<Salary>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("amount", it.amount).put("time", it.time).put("extra", it.extra).put("note", it.note))
        }
    }
}

/** User's name (app) and nickname (widget). */
object Profile {
    fun name(c: Context): String = prefs(c).getString("p_name", "") ?: ""
    fun nick(c: Context): String = prefs(c).getString("p_nick", "") ?: ""
    fun save(c: Context, name: String, nick: String) {
        prefs(c).edit().putString("p_name", name.trim()).putString("p_nick", nick.trim()).apply()
        BudgetWidget.refresh(c)
    }
}

/** Bank accounts and cards, for sharing when someone sends money. */
object Wallet {
    fun banks(c: Context): List<Bank> = try {
        val a = JSONArray(prefs(c).getString("banks", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Bank(o.getLong("id"), o.optString("bank"), o.optString("account"), o.optString("branch"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun cards(c: Context): List<CardInfo> = try {
        val a = JSONArray(prefs(c).getString("cards", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            CardInfo(o.getLong("id"), o.optString("holder"), o.optString("number"), o.optString("bank"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveBank(c: Context, b: Bank) {
        val list = banks(c)
        val item = if (b.id == 0L) b.copy(id = newId()) else b
        val out = if (list.any { it.id == item.id }) list.map { if (it.id == item.id) item else it } else list + item
        prefs(c).edit().putString("banks", banksJson(out).toString()).apply()
    }

    fun deleteBank(c: Context, id: Long) {
        prefs(c).edit().putString("banks", banksJson(banks(c).filter { it.id != id }).toString()).apply()
    }

    fun saveCard(c: Context, card: CardInfo) {
        val list = cards(c)
        val item = if (card.id == 0L) card.copy(id = newId()) else card
        val out = if (list.any { it.id == item.id }) list.map { if (it.id == item.id) item else it } else list + item
        prefs(c).edit().putString("cards", cardsJson(out).toString()).apply()
    }

    fun deleteCard(c: Context, id: Long) {
        prefs(c).edit().putString("cards", cardsJson(cards(c).filter { it.id != id }).toString()).apply()
    }

    fun banksJson(list: List<Bank>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("bank", it.bank).put("account", it.account).put("branch", it.branch)) }
    }

    fun cardsJson(list: List<CardInfo>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("holder", it.holder).put("number", it.number).put("bank", it.bank)) }
    }

    fun groupCard(n: String): String = n.chunked(4).joinToString(" ")
    fun maskCard(n: String): String =
        if (n.length <= 4) n else "•••• ".repeat(((n.length - 4) + 3) / 4).trimEnd() + " " + n.takeLast(4)

    fun bankText(c: Context, b: Bank): String = buildString {
        appendLine("Bank details")
        val owner = Profile.name(c)
        if (owner.isNotBlank()) appendLine("Account name: $owner")
        appendLine("Bank: ${b.bank}")
        appendLine("Account number: ${b.account}")
        append("Branch: ${b.branch}")
    }

    fun cardText(card: CardInfo): String = buildString {
        appendLine("Card details")
        appendLine("Card holder: ${card.holder}")
        appendLine("Card number: ${groupCard(card.number)}")
        append("Bank: ${card.bank}")
    }

    fun allText(c: Context): String {
        val parts = banks(c).map { bankText(c, it) } + cards(c).map { cardText(it) }
        return parts.joinToString("\n\n")
    }

    fun share(c: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(send, "Share details").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(c: Context, text: String) {
        c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Details", text))
        Toast.makeText(c, "Copied", Toast.LENGTH_SHORT).show()
    }
}

/** Widget theme and background opacity. Stored separately so "Clear all data" keeps them. */
object WidgetLook {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("budget_widget", Context.MODE_PRIVATE)

    fun style(c: Context): WidgetStyle = WidgetStyle.values().getOrElse(p(c).getInt("style", 0)) { WidgetStyle.AMOLED }
    fun setStyle(c: Context, s: WidgetStyle) = p(c).edit().putInt("style", s.ordinal).apply()

    fun opacity(c: Context) = p(c).getInt("opacity", 100).coerceIn(0, 100)
    fun setOpacity(c: Context, v: Int) = p(c).edit().putInt("opacity", v.coerceIn(0, 100)).apply()
}

/** Widget themes. Colours are ARGB. */
enum class WidgetStyle(
    val label: String,
    val bgRes: Int,
    val bg: Long,
    val stroke: Long,
    val primary: Long,
    val secondary: Long,
    val ghost: Long
) {
    AMOLED("AMOLED", R.drawable.wbg_amoled, 0xFF000000, 0xFF262626, 0xFFFFFFFF, 0xFF8A8A8A, 0x26FFFFFF),
    LIGHT("LIGHT", R.drawable.wbg_light, 0xFFFFFFFF, 0xFFE2E2E2, 0xFF000000, 0xFF6E6E6E, 0x24000000),
    CLEAN("CLEAN", R.drawable.wbg_clean, 0xFF1C1C1E, 0x00000000, 0xFFF2F2F2, 0xFF9A9A9A, 0x22FFFFFF);

    val primaryInt get() = primary.toInt()
    val secondaryInt get() = secondary.toInt()
    val ghostInt get() = ghost.toInt()
}

object Fmt {
    private val moneyFmt = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    fun money(v: Double): String = (if (v < 0) "-" else "") + moneyFmt.format(abs(v))

    fun key(s: String): String = s.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

    fun title(s: String): String =
        key(s).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }

    fun day(t: Long): String = SimpleDateFormat("dd MMM", Locale.US).format(Date(t)).uppercase(Locale.US)
    fun month(t: Long): String = SimpleDateFormat("MMM", Locale.US).format(Date(t)).uppercase(Locale.US)
    fun dateTime(t: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(t))
    fun today(): String = SimpleDateFormat("EEE dd MMM", Locale.US).format(Date()).uppercase(Locale.US)

    fun daysToPayday(payday: Int): Int {
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_MONTH)
        val todayMax = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (today == minOf(payday, todayMax)) return 0
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            if (today > minOf(payday, todayMax)) add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, minOf(payday, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diff = target.timeInMillis - start.timeInMillis
        return ((diff + TimeUnit.HOURS.toMillis(12)) / TimeUnit.DAYS.toMillis(1)).toInt()
    }
}
