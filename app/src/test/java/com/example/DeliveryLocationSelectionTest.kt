package com.example

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.robotoperator.data.model.Destination
import com.example.robotoperator.ui.screens.deliveries.DeliveryLocationSelection
import com.example.ui.theme.RobotOperatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeliveryLocationSelectionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sourceSelectionFiltersDropoffsAndResetsPreviousDestination() {
        val source = Destination("a", "Reception", 0.0, 0.0, map_id = "floor1", robot_id = "r1")
        val locations = listOf(source, source.copy(id = "b", name = "Lab"),
            source.copy(id = "c", name = "Other floor", map_id = "floor2"))
        rule.setContent {
            var sourceId by remember { mutableStateOf<String?>(null) }
            var destinationId by remember { mutableStateOf<String?>(null) }
            RobotOperatorTheme {
                DeliveryLocationSelection(locations, sourceId, destinationId, true,
                    { sourceId = it; destinationId = null }, { destinationId = it })
            }
        }
        rule.onNodeWithTag("destination_b").assertDoesNotExist()
        rule.onNodeWithTag("source_a").performClick()
        rule.onNodeWithTag("destination_a").assertDoesNotExist()
        rule.onNodeWithTag("destination_c").assertDoesNotExist()
        rule.onNodeWithTag("destination_b").performClick()
        rule.onNodeWithTag("destination_b").assertTextContains("Selected: Lab")
        rule.onNodeWithTag("source_b").performClick()
        rule.onNodeWithTag("destination_b").assertDoesNotExist()
        rule.onNodeWithTag("destination_a").assertTextContains("Reception")
    }
}
