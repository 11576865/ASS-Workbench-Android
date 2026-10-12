# 模拟器失败清单：已取得真实断言

## 5f5c904b 编译阻断与画布操作可达性

Android CI run38250102462、Fontconfig run38250102470成功；Emulator run38250102523失败。独立证据job114816059291列出五轮NO XML RESULTS。进一步读取原始job114813871580，五轮均在compileDebugAndroidTestKotlin失败：EditorRegressionInstrumentedTest.kt:9/1704 Unresolved reference 'hasWindowFocus'。本轮没有执行原生断言，不能据此判断上一轮24项失败是否减少。

移除不存在的ViewMatchers.hasWindowFocus导入，使用测试内TypeSafeMatcher<View>调用View.hasWindowFocus，再交给withDecorView。保留对实际焦点窗口执行与观察IME的逻辑、五秒期限和null不能代表关闭的检查。Android CI新增compileDebugAndroidTestKotlin门禁，在模拟器之外也编译测试源码。

生产画布顶栏的缩放、总览、召回属于horizontalScroll操作行，鸟瞰入口独立固定。现有测试直接点击屏外语义节点：改为performScrollTo → assertIsDisplayed → performClick，覆盖语义缩放、普通草稿保留、召回和文档/Undo不变用例。此为已确认操作路径校正，尚不证明历史缩放/召回失败全部由此引起；保留原有结果断言，不使用屏外语义动作绕过触控。

本地无Android SDK，本轮完成源码、差异和artifact policy检查；新的测试编译与模拟器结果待CI。未修改领域逻辑，不沿用267项领域测试作为本轮验证。

## 多选时搜索入口

sharedSubtitleFilterNavigatesResultsWithoutChangingSelectionOrDocumentAcrossHosts实际先toggleSelected(1L)，再点击搜索。生产顶栏selectionMode分支只有退出、计数、全选、批量，搜索只存在于else分支。因此缺少搜索节点是确认的产品入口缺口。沿用已批准的筛选/选择并存语义，在多选分支也提供同一onSearchToggle入口；不通过清空选择来修复测试。既有失败回归继续验证筛选、定位和文档/选择不被导航暗中更改。新原生执行Pending。


## c7dbcd74 最终结果与焦点窗口修复

来源run38246400875/attempt1，回归job114801352170，证据job114808156236成功。Android38246400860和Fontconfig38246400843成功。投影四组仍22/22无失败；完整146项、24失败、0 errors、0 skipped。测试集合由142变为146，不能用总数变化计算完成率。

- `batchUnknownStyleAndNegativeMarginFailBeforePreview`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `rectClipExternalTakeoverCancelsPendingDraftAndRestoresAllCoordinates`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `groupedNavigationAndGeometrySectionsPreserveDocument`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `sharedSubtitleFilterNavigatesResultsWithoutChangingSelectionOrDocumentAcrossHosts`：java.lang.AssertionError: Failed to inject touch input.
- `batchInvalidRegexFailsClosedInsteadOfDroppingTheFilter`：java.lang.AssertionError: Assert failed: The component with TestTag = 'batch-preview-pending' is not displayed!
- `transformSliderStillOwnsItsPreviewAndCommitsOneUndoStep`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `inspectorDraftSurvivesToolSwitchAndRotation`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `batchKaraokeRevealControlsStayPreviewOnlyUntilCommit`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `spatialWorkspaceLongPressDragExtractsPositionWithoutEditingAss`：java.lang.AssertionError: Action performScrollTo() failed.
- `semanticSearchInvalidAuxiliaryFiltersStayFailClosed`：java.lang.AssertionError: Assert failed: The component with TestTag = 'search-style-regex' is not displayed!
- `unifiedCanvasHidesAndRecallsNativeToolDirectory`：java.lang.AssertionError: Failed to inject touch input.
- `spatialWorkspaceExposesBoardAndNativeEditorWithoutMovingWorldNodes`：java.lang.AssertionError: Assert failed: The component with TestTag = 'spatial-edge-rail-left' is not displayed!
- `effectsPaneExposesSpatialFadeAuthoringWithoutMutatingDocument`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `semanticSearchInvalidAuxiliaryFiltersFailClosedInUi`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `batchActionsRemainAboveVisibleKeyboardAcrossMainAndCanvas`：androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms
- `batchSearchLiteralInputsRestoreButReviewedCommitRequiresFreshPrecheckAfterRecreation`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=false windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `transformFieldsProjectExternalPreviewAndRestoreDrafts`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `fixedInspectorSharesUncommittedEventDraftAcrossToolInstances`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=false windowFocused=false focusClass=AndroidComposeView focusHasToken=true focusTokenMatchesDecor=true
- `karaokeFxPreviewIsNonDestructiveAndBatchApplyCommitsAtomically`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `independentDirectoryDismissalPreservesDirtyTextAndTaskContext`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `metadataDraftFollowsHostAndRetainsExternalConflictUntilExplicitApply`：java.lang.AssertionError: IME hide did not complete: accepted=false beforeVisible=true windowFocused=false focusClass=null focusHasToken=false focusTokenMatchesDecor=false
- `semanticZoomDoesNotDestroyNonSaveableEditorDraft`：java.lang.AssertionError: Failed: assertExists.
- `closeUpBoardKeepsMultipleRealToolsAndOverviewUsesSummaries`：java.lang.AssertionError: Failed: assertDoesNotExist.
- `spatialCameraAndOverlayDoNotEditDocument`：java.lang.AssertionError: Failed to inject touch input.

IME失败的边界证据为accepted=false、windowFocused=false，常伴随Activity.currentFocus=null；固定工具目录使用AlertDialog独立Window，原hideKeyboard却向Activity.decorView.windowToken发请求并观察Activity的insets。这能定位已观测的目录窗口收起路径，不能自动解释全部失败。

改为Espresso onView(isRoot()).inRoot(withDecorView(hasWindowFocus()))定位实际焦点窗口，在该root执行标准closeSoftKeyboard，并观察同一root的IME visibility必须为false；null不当作关闭。保留5秒期限、原始cause、实际显示/文档/草稿/Undo断言。增加明确让Dialog键盘出现后关闭的回归，检查目录不被关闭、搜索字面输入和未提交正文保留；显式声明espresso-core测试依赖，与现有Test1.6系列对齐。不改产品键盘实现。

新测试和时间草稿一起接回PR138，Android编译与原生执行待CI。本地无SDK，仅source/diff/artifact policy检查，未新增领域逻辑、不重复计为领域测试通过。


## 内容边界与批量触控校准

071c9818 Android CI38244659459成功；对应Fontconfig38244659452和Emulator38244659428检查时仍运行，IME新增诊断尚无本轮结果。

侧书签断言明确比较了错误区域：spatial-focused-editor包含全宽标题和动作条，而生产侧轨从113dp以下开始，只对内容预留横向空间。测试改为比较spatial-native-content-STYLE-primary与侧轨边界，并额外检查标题位于侧轨上方；原世界几何、实例身份和召回断言不变。真实内容重叠仍会失败，不能仅因容器宽度相交判定控件遮挡。

批量“应用0条”断言之前点击预检时没有确认显示状态。窄屏主UI的固定工具底栏仍处于fixed-content-flow外层滚动区域内，scope/参数改变不会自动滚到该底栏。新增visibleBatchAction仅在该外层flow存在时请求真实滚动，然后必须assertIsDisplayed，再点击；画布/宽屏不调用不存在的滚动祖先。三项规则范围/跨Host草稿/搜索恢复用例保留全部预览、文档和Undo断言。键盘可见时固定底栏的严格位置/触控测试不使用此滚动助手，不能用滚动掩盖IME避让问题。

这是可达性与证据对象的校准，不证明批量0条的最终根因，也不宣称原生用例已通过。新回归待CI，本地仅完成源码核对、diff和artifact policy检查，产品行为无修改。

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

