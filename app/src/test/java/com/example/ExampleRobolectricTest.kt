package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.security.SecurityLevel
import com.example.aura.di.AuraContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aura", appName)
  }

  @Test
  fun `verify aura container and architecture layers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = AuraContainer(context)

    assertNotNull(container.database)
    assertNotNull(container.repository)
    assertNotNull(container.providerRegistry)
    assertNotNull(container.toolRegistry)
    assertNotNull(container.approvalManager)
    assertNotNull(container.agent)

    // Verify tools registered
    val activeTools = container.toolRegistry.getActiveTools()
    assertTrue(activeTools.isNotEmpty())
    assertTrue(activeTools.any { it.id == "system_diagnostics" })
    assertTrue(activeTools.any { it.id == "network_check" })
    assertTrue(activeTools.any { it.id == "sandboxed_file" })
    assertTrue(activeTools.any { it.id == "propose_notification" })

    // Verify security approval policy defaults
    assertEquals(ApprovalPolicy.STANDARD, container.approvalManager.currentPolicy.value)
    assertTrue(container.approvalManager.requiresApproval(SecurityLevel.SENSITIVE))
    assertTrue(container.approvalManager.requiresApproval(SecurityLevel.CRITICAL))
    assertEquals(false, container.approvalManager.requiresApproval(SecurityLevel.SAFE))
  }
}

