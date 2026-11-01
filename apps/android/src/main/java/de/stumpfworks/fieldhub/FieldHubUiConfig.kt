package de.stumpfworks.fieldhub

import android.content.Context
import org.json.JSONObject

internal data class UiNavigationItem(val screen: String, val label: String, val minRole: String?)
internal data class UiWorkspaceConfig(val id: String, val navigation: List<UiNavigationItem>)
internal data class FieldHubUiConfig(val schemaVersion: Int, val workspaces: List<UiWorkspaceConfig>) {
    fun navigationFor(workspaceId: String, isRavensStaff: Boolean): List<UiNavigationItem> =
        workspaces.firstOrNull { it.id == workspaceId }?.navigation.orEmpty()
            .filter { item -> item.minRole != "coach" || isRavensStaff }
}

// Only a known set of screens may be rendered. JSON is configuration, never executable code.
private val supportedScreens = setOf("today", "events", "team", "recruiting", "tasks", "profile")

internal fun loadLocalUiConfig(context: Context): FieldHubUiConfig {
    val json = context.assets.open("fieldhub_ui_v1.json").bufferedReader().use { it.readText() }
    return parseUiConfig(json)
}

internal fun parseUiConfig(json: String): FieldHubUiConfig {
    val root = JSONObject(json)
    require(root.getInt("schemaVersion") == 1) { "Unsupported UI schema version" }
    val workspaces = root.getJSONArray("workspaces")
    require(workspaces.length() <= 50) { "Too many workspaces" }
    val configs = (0 until workspaces.length()).map { i ->
        val workspace = workspaces.getJSONObject(i)
        val nav = workspace.getJSONArray("navigation")
        require(nav.length() <= 12) { "Too many navigation items" }
        val items = (0 until nav.length()).map { j ->
            val item = nav.getJSONObject(j)
            val screen = item.getString("screen")
            require(screen in supportedScreens) { "Unsupported screen: $screen" }
            val label = item.getString("label")
            require(label.length in 1..40) { "Invalid navigation label" }
            UiNavigationItem(screen, label, item.optString("minRole").takeIf { it.isNotBlank() })
        }
        require(items.map { it.screen }.distinct().size == items.size) { "Duplicate navigation screen" }
        UiWorkspaceConfig(workspace.getString("id"), items)
    }
    require(configs.map { it.id }.distinct().size == configs.size) { "Duplicate workspace ID" }
    return FieldHubUiConfig(1, configs)
}
