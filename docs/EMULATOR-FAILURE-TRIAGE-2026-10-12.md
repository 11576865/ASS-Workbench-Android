# 模拟器失败清单：已取得真实断言

## 已定位的测试入口问题

7d069410 Android CI38242900337成功，字段校验/元数据共享的Compose集成已编译；对应模拟器和Fontconfig本轮检查时仍运行。

确认源码根因：EditorRegressionInstrumentedTest.openTool的画布分支仍点击workspace-tools，生产源码已无该tag；InfiniteCanvasHost在画布和聚焦页均通过spatial-add-tool → showToolPicker → onAddTool进入真实WorkspaceToolDirectory，其他三个既有回归已使用该入口。本轮更新公共助手并等待tool-search出现，保留实际显示、文档、草稿和Undo断言。这修复了明确的测试导航错误，尚不代表相关原生测试已通过。

IME等待失败保留原五秒期限和失败判定。新增错误上下文记录hideSoftInput请求返回值、调用前IME可见性、窗口焦点、焦点视图类型和token关系，不记录输入正文；超时仍失败并保留原cause。先取得边界证据，再判断收起请求未被接受、窗口/token异常或后续重开等竞争解释。本轮不修改产品键盘行为，不将IME失败归因于目录旧tag，也不删除原控件可见性检查。

来源：952364b7；Emulator run38240367109/attempt1，回归job114781182675，独立证据job114787971310成功。实际执行的PR merge SHA为64a894c370b95d1bee8fde7d02d0197576b008f5。Android CI38240367088及Fontconfig38240367092成功。

四个独立投影尝试分别9、3、7、3项，全部无失败；完整回归142项，29失败、0 errors、0 skipped。报告通道成功不代表回归通过。此次数量不证明完整预期测试集全部执行，原始job退出码继续是失败。

失败包含IME等待超时、入口节点缺失、控件未显示、批量预览应用0条以及侧轨遮挡。以下只记录断言事实；没有证明统一根因，不通过删除断言或改成检查存在来掩盖真实可达性要求。

- `batchUnknownStyleAndNegativeMarginFailBeforePreview`：`java.lang.AssertionError: Assert failed: The component with TestTag = 'batch-style-action' is not displayed!`
- `rectClipExternalTakeoverCancelsPendingDraftAndRestoresAllCoordinates`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `groupedNavigationAndGeometrySectionsPreserveDocument`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `batchDraftAndReviewedPreviewFollowSameInstanceAcrossMainAndCanvas`：`java.lang.AssertionError: Failed to assert the following: (is enabled)`
- `sharedSubtitleFilterNavigatesResultsWithoutChangingSelectionOrDocumentAcrossHosts`：`java.lang.AssertionError: Failed to inject touch input.`
- `batchInvalidRegexFailsClosedInsteadOfDroppingTheFilter`：`java.lang.AssertionError: Assert failed: The component with TestTag = 'batch-preview-pending' is not displayed!`
- `transformSliderStillOwnsItsPreviewAndCommitsOneUndoStep`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `eventTextDraftFollowsTheSameToolAcrossMainAndCanvasWithoutImplicitCommit`：`java.lang.AssertionError: Failed to inject touch input.`
- `inspectorDraftSurvivesToolSwitchAndRotation`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `batchKaraokeRevealControlsStayPreviewOnlyUntilCommit`：`java.lang.AssertionError: Assert failed: The component with TestTag = 'batch-karaoke-reveal' is not displayed!`
- `semanticSearchInvalidAuxiliaryFiltersStayFailClosed`：`java.lang.AssertionError: Assert failed: The component with TestTag = 'search-style-regex' is not displayed!`
- `unifiedCanvasHidesAndRecallsNativeToolDirectory`：`java.lang.AssertionError: Failed to inject touch input.`
- `spatialWorkspaceExposesBoardAndNativeEditorWithoutMovingWorldNodes`：`java.lang.AssertionError: Assert failed: The component with TestTag = 'spatial-edge-rail-left' is not displayed!`
- `effectsPaneExposesSpatialFadeAuthoringWithoutMutatingDocument`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `semanticSearchInvalidAuxiliaryFiltersFailClosedInUi`：`java.lang.AssertionError: Assert failed: The component with TestTag = 'search-style-regex' is not displayed!`
- `batchActionsRemainAboveVisibleKeyboardAcrossMainAndCanvas`：`java.lang.AssertionError: Failed to inject touch input.`
- `batchSearchLiteralInputsRestoreButReviewedCommitRequiresFreshPrecheckAfterRecreation`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `transformFieldsProjectExternalPreviewAndRestoreDrafts`：`java.lang.AssertionError: Failed to perform text input.`
- `equalExternalTakeoverCancelsPendingNumericCommit`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `compactContentFlowCanScrollPreviewOutWithoutLosingDirtyText`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `batchUsesSharedResultsAndRequiresFreshPreviewAfterScopeChangeAcrossHosts`：`java.lang.AssertionError: Failed to assert the following: (is enabled)`
- `karaokeFxPreviewIsNonDestructiveAndBatchApplyCommitsAtomically`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `fixedTaskNavigationAndExternalToolCommandKeepTheRequestedEditorConsistent`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `independentDirectoryDismissalPreservesDirtyTextAndTaskContext`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `positionFieldsFollowTransientPreviewAndCancelRestoresTypedDraft`：`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`
- `semanticZoomDoesNotDestroyNonSaveableEditorDraft`：`java.lang.AssertionError: Failed: assertExists.`
- `closeUpBoardKeepsMultipleRealToolsAndOverviewUsesSummaries`：`java.lang.AssertionError: Failed: assertDoesNotExist.`
- `unifiedCanvasSideBookmarksReuseToolIdentityAndPersistWorldGeometry`：`java.lang.AssertionError: Bookmark rail must not cover native editor controls`
- `spatialCameraAndOverlayDoNotEditDocument`：`java.lang.AssertionError: Failed to inject touch input.`

优先后续：核对hideKeyboard在有焦点或SHOW_FORCED状态的收起流程；核对Host切换完成与工具目录的可见入口；区分固定底栏/滚动正文的显示断言；检查review变为应用0条的实际配方及收据状态；独立复现书签轨覆盖。以上是调查顺序，不是已确认产品根因。新增字段校验和元数据共享草稿均未在本来源提交中执行，不能以其解释这些失败。

