package io.github.assworkbench.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.ContainerBridgePanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContainerBridgePanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        viewModel = EditorViewModel(
            ApplicationProvider.getApplicationContext<Application>()
        )
    }

    @Test
    fun containerInventoryGroupsTracksAttachmentsAndContainerInfo() {
        val state = ContainerBridgeState(
            uri = "content://fixture/source.mkv",
            name = "source.mkv",
            writeBackAvailable = true,
            resources = listOf(
                ContainerResourceUi(
                    rowKey = "track:uid:101",
                    kind = ContainerResourceKind.VIDEO,
                    title = "Main video",
                    detail = "V_VP9 · Track #1",
                    trackNumber = 1L,
                    trackTarget = "uid:101",
                    trackUid = 101L,
                    trackCodecId = "V_VP9",
                ),
                ContainerResourceUi(
                    rowKey = "attachment:uid:7",
                    kind = ContainerResourceKind.ATTACHMENT,
                    title = "cover.png",
                    detail = "image/png",
                    attachmentTarget = "7",
                    attachmentMimeType = "image/png",
                ),
                ContainerResourceUi(
                    rowKey = "chapters",
                    kind = ContainerResourceKind.CHAPTERS,
                    title = "章节",
                    detail = "3 chapters",
                ),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                ContainerBridgePanel(
                    state = state,
                    editPlan = ContainerEditPlanUi(
                        mutations = emptyList(),
                        checks = emptyList(),
                    ),
                    viewModel = viewModel,
                    onSaveMkv = {},
                    dirty = false,
                )
            }
        }

        composeRule.onNodeWithText("轨道").assertIsDisplayed()
        composeRule.onNodeWithText("附件").assertIsDisplayed()
        composeRule.onNodeWithText("容器信息").assertIsDisplayed()
        composeRule.onNodeWithText("添加轨道").assertIsDisplayed()
        composeRule.onNodeWithText("添加附件").assertIsDisplayed()
        composeRule.onNodeWithText("保存新 MKV").assertIsDisplayed()
    }

    @Test
    fun secondaryTrackActionsStayBehindOverflowMenu() {
        val state = ContainerBridgeState(
            uri = "content://fixture/source.mkv",
            name = "source.mkv",
            writeBackAvailable = true,
            resources = listOf(
                ContainerResourceUi(
                    rowKey = "track:uid:101",
                    kind = ContainerResourceKind.VIDEO,
                    title = "Main video",
                    detail = "V_VP9 · Track #1",
                    trackNumber = 1L,
                    trackTarget = "uid:101",
                    trackUid = 101L,
                    trackCodecId = "V_VP9",
                    trackName = "Main video",
                    trackLanguage = "und",
                    trackIsDefault = true,
                ),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                ContainerBridgePanel(
                    state = state,
                    editPlan = ContainerEditPlanUi(
                        mutations = emptyList(),
                        checks = emptyList(),
                    ),
                    viewModel = viewModel,
                    onSaveMkv = {},
                    dirty = false,
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("资源操作")[0]
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("轨道信息").assertIsDisplayed()
        composeRule.onNodeWithText("删除轨道").assertIsDisplayed()
    }
}
