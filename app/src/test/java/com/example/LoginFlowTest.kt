package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.ui.screens.auth.LoginScreen
import com.example.ui.theme.RobotOperatorTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginFlowTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("robot_operator_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = ServerSettingsStore(context)
        val auth = AuthManager(ApiClient(settings))
        rule.setContent {
            RobotOperatorTheme {
                LoginScreen(auth, settings, {}, { error("Unexpected authentication") })
            }
        }
    }

    @Test fun userCanOpenRegistrationAndReturnToLogin() {
        rule.onNodeWithTag("choose_user_login").performClick()
        rule.onNodeWithTag("auth_heading").assertTextEquals("User login")
        rule.onNodeWithText("New user? Create an account").performScrollTo().performClick()
        rule.onNodeWithTag("register_name_input").assertExists()
        rule.onNodeWithText("Already registered? Sign in").performScrollTo().performClick()
        rule.onNodeWithTag("register_name_input").assertDoesNotExist()
    }

    @Test fun adminPortalHasNoSelfRegistration() {
        rule.onNodeWithTag("choose_admin_login").performClick()
        rule.onNodeWithTag("auth_heading").assertTextEquals("Admin login")
        rule.onNodeWithText("New user? Create an account").assertDoesNotExist()
        rule.onNodeWithTag("login_email_input").assertExists()
        rule.onNodeWithTag("login_password_input").assertExists()
    }

    @Test fun unconfiguredInstallBlocksLoginWithUsefulMessage() {
        rule.onNodeWithTag("choose_user_login").performClick()
        rule.onNodeWithTag("sign_in_button").performScrollTo().performClick()
        rule.onNodeWithTag("auth_error").assertTextContains("connection has not been set up", substring = true)
    }
}
