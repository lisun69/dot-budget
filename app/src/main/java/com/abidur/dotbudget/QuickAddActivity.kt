package com.abidur.dotbudget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Small pop-up opened from the widget's red + button. */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val suggestions = Store.load(this).suggestions()
        setContent {
            NothingTheme {
                Box(
                    Modifier.fillMaxSize().background(Color(0xB3000000))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
                        .imePadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.padding(20.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    ) {
                        EntryCard(
                            mode = EntryMode.SPEND,
                            suggestions = suggestions,
                            onSave = { name, amt ->
                                Store.addExpense(this@QuickAddActivity, name, amt)
                                Toast.makeText(this@QuickAddActivity, "Saved: ${Fmt.title(name)} ${Fmt.money(amt)} TK", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onCancel = { finish() }
                        )
                    }
                }
            }
        }
    }
}
