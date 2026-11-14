package com.abidur.dotbudget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

/** Resizable home-screen widget with themes and adjustable opacity. */
class BudgetWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { draw(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        draw(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private val RED = 0xFFD71921.toInt()

        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, BudgetWidget::class.java)).forEach { draw(context, mgr, it) }
        }

        private fun draw(context: Context, mgr: AppWidgetManager, id: Int) {
            val s = Store.load(context)
            val c = s.current
            val opts = mgr.getAppWidgetOptions(id)
            val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 180
            val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 180

            val st = WidgetLook.style(context)
            val pri = st.primaryInt
            val sec = st.secondaryInt
            val ghost = st.ghostInt

            val v = RemoteViews(context.packageName, R.layout.widget_budget)

            // theme + opacity
            v.setImageViewResource(R.id.bg, st.bgRes)
            v.setInt(R.id.bg, "setImageAlpha", WidgetLook.opacity(context) * 255 / 100)
            v.setInt(R.id.settings_btn, "setColorFilter", sec)
            intArrayOf(R.id.title, R.id.payday, R.id.sub, R.id.spent).forEach { v.setTextColor(it, sec) }
            intArrayOf(R.id.recent1, R.id.recent2, R.id.recent3).forEach { v.setTextColor(it, pri) }

            // header: BUDGET · nickname
            val nick = Profile.nick(context)
            v.setTextViewText(R.id.title, if (nick.isBlank()) "BUDGET" else "BUDGET · ${nick.uppercase(Locale.US)}")

            v.setImageViewBitmap(R.id.amount_img, DotFont.bitmap(Fmt.money(c.left), 16, if (c.left < 0) RED else pri, ghost))
            v.setTextViewText(R.id.sub, if (c.hasSalary) "TK LEFT OF ${Fmt.money(c.income)}" else "SET SALARY IN APP")
            val d = Fmt.daysToPayday(s.payday)
            v.setTextViewText(R.id.payday, if (d == 0) "PAYDAY" else "PAYDAY ${d}D")
            v.setTextViewText(R.id.spent, "SPENT ${Fmt.money(c.spent)}")

            val frac = if (c.income > 0) (c.left / c.income).coerceIn(0.0, 1.0) else 0.0
            v.setImageViewBitmap(
                R.id.progress_img,
                DotFont.progressBitmap(if (w < 200) 14 else 20, frac, if (frac < 0.2) RED else pri, ghost, 16)
            )

            val recent = c.expenses.take(3)
            intArrayOf(R.id.recent1, R.id.recent2, R.id.recent3).forEachIndexed { i, rid ->
                if (i < recent.size) {
                    v.setTextViewText(rid, "${recent[i].name.uppercase(Locale.US)}  -${Fmt.money(recent[i].amount)}")
                    v.setViewVisibility(rid, View.VISIBLE)
                } else v.setViewVisibility(rid, View.GONE)
            }

            // size rules
            val tiny = h < 100
            v.setViewVisibility(R.id.header, if (tiny) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.sub, if (tiny) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.progress_img, if (h < 140) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.recent_box, if (h >= 210 && recent.isNotEmpty()) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.payday, if (w < 150) View.GONE else View.VISIBLE)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            v.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags
                )
            )
            v.setOnClickPendingIntent(
                R.id.add_btn,
                PendingIntent.getActivity(context, 1, Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            )
            v.setOnClickPendingIntent(
                R.id.settings_btn,
                PendingIntent.getActivity(
                    context, 2,
                    Intent(context, WidgetSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK), flags
                )
            )
            mgr.updateAppWidget(id, v)
        }
    }
}
