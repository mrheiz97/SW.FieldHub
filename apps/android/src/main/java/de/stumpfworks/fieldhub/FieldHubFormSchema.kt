package de.stumpfworks.fieldhub

import org.json.JSONObject

/** Declarative forms contain only supported fields; never executable expressions or code. */
internal data class UiFormField(
    val id: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val options: List<String>
)
internal data class UiFormSchema(val id: String, val title: String, val fields: List<UiFormField>)

internal fun parseFormSchema(json: String): UiFormSchema {
    val root = JSONObject(json)
    require(root.getInt("schemaVersion") == 1) { "Unsupported form schema" }
    val form = root.getJSONObject("form")
    val id = form.getString("id")
    val title = form.getString("title")
    require(id.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,63}"))) { "Invalid form ID" }
    require(title.length in 1..80) { "Invalid form title" }
    val raw = form.getJSONArray("fields")
    require(raw.length() in 1..30) { "Invalid number of fields" }
    val allowedTypes = setOf("text", "email", "phone", "multiline", "select", "checkbox")
    val fields = (0 until raw.length()).map { i ->
        val field = raw.getJSONObject(i)
        val fieldId = field.getString("id")
        val label = field.getString("label")
        val type = field.getString("type")
        require(fieldId.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,63}"))) { "Invalid field ID" }
        require(label.length in 1..80 && type in allowedTypes) { "Unsupported form field" }
        val rawOptions = field.optJSONArray("options")
        val options = rawOptions?.let { array ->
            require(array.length() in 1..20) { "Invalid options" }
            (0 until array.length()).map { index ->
                array.getString(index).also { require(it.length in 1..80) { "Invalid option" } }
            }
        }.orEmpty()
        require((type == "select") == options.isNotEmpty()) { "Select fields require options" }
        require(options.distinct().size == options.size) { "Duplicate options" }
        UiFormField(fieldId, label, type, field.optBoolean("required", false), options)
    }
    require(fields.map { it.id }.distinct().size == fields.size) { "Duplicate field ID" }
    return UiFormSchema(id, title, fields)
}

/** Returns field IDs with invalid values; used before every local save. */
internal fun validateFormValues(schema: UiFormSchema, values: Map<String, String>): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    schema.fields.forEach { field ->
        val value = values[field.id].orEmpty().trim()
        val error = when {
            value.length > 4000 -> "Eingabe ist zu lang"
            field.required && (value.isBlank() || (field.type == "checkbox" && value != "true")) -> "Pflichtfeld"
            value.isBlank() -> null
            field.type == "email" && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "Ungültige E-Mail-Adresse"
            field.type == "phone" && !value.matches(Regex("[+0-9() /-]{3,40}")) -> "Ungültige Telefonnummer"
            field.type == "select" && value !in field.options -> "Ungültige Auswahl"
            field.type == "checkbox" && value != "true" && value != "false" -> "Ungültiger Wert"
            else -> null
        }
        if (error != null) errors[field.id] = error
    }
    return errors
}
