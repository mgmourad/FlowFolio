package com.flowfolio.tools.convert

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Checks a sidecar against `docs/schemas/flowfolio.schema.json`: the subset of JSON Schema that file uses (type,
 * required, properties, additionalProperties, items, enum, pattern, minimum, maximum, minItems, maxItems). Enough for
 * our own schema without another dependency; returns what doesn't conform, empty when it all does.
 */
class SchemaCheck(schemaFile: File) {
    private val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject

    fun problems(json: String): List<String> = ArrayList<String>().also {
        check(Json.parseToJsonElement(json), schema, "$", it)
    }

    private fun check(value: JsonElement, s: JsonObject, at: String, out: MutableList<String>) {
        s["enum"]?.let { allowed ->
            if (value !in allowed.jsonArray) out += "$at: $value is not one of ${allowed.jsonArray}"
        }
        s["type"]?.let { t ->
            val types = (t as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOf(t.jsonPrimitive.content)
            if (types.none { isType(value, it) }) {
                out += "$at: expected $types, got $value"
                return
            }
        }
        when (value) {
            is JsonObject -> checkObject(value, s, at, out)
            is JsonArray -> checkArray(value, s, at, out)
            is JsonPrimitive -> checkPrimitive(value, s, at, out)
        }
    }

    private fun checkObject(value: JsonObject, s: JsonObject, at: String, out: MutableList<String>) {
        val props = s["properties"]?.jsonObject ?: JsonObject(emptyMap())
        s["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.forEach {
            if (it !in
                value
            ) {
                out += "$at: missing $it"
            }
        }
        val extra = s["additionalProperties"]
        for ((k, v) in value) {
            val ps = props[k]?.jsonObject
            when {
                ps != null -> check(v, ps, "$at.$k", out)
                extra is JsonObject -> check(v, extra, "$at.$k", out)
                extra?.jsonPrimitive?.booleanOrNull == false -> out += "$at: unexpected $k"
            }
        }
    }

    private fun checkArray(value: JsonArray, s: JsonObject, at: String, out: MutableList<String>) {
        s["minItems"]?.jsonPrimitive?.intOrNull?.let { if (value.size < it) out += "$at: fewer than $it items" }
        s["maxItems"]?.jsonPrimitive?.intOrNull?.let { if (value.size > it) out += "$at: more than $it items" }
        val items = s["items"]?.jsonObject ?: return
        value.forEachIndexed { i, v -> check(v, items, "$at[$i]", out) }
    }

    private fun checkPrimitive(value: JsonPrimitive, s: JsonObject, at: String, out: MutableList<String>) {
        if (value is JsonNull) return
        val n = value.doubleOrNull.takeUnless { value.isString }
        s["minimum"]?.jsonPrimitive?.doubleOrNull?.let { if (n != null && n < it) out += "$at: $n below $it" }
        s["maximum"]?.jsonPrimitive?.doubleOrNull?.let { if (n != null && n > it) out += "$at: $n above $it" }
        s["pattern"]?.jsonPrimitive?.content?.let {
            if (value.isString && !Regex(it).containsMatchIn(value.content)) out += "$at: '${value.content}' !~ $it"
        }
    }

    // One branch per JSON Schema type.
    @Suppress("CyclomaticComplexMethod")
    private fun isType(v: JsonElement, type: String): Boolean = when (type) {
        "object" -> v is JsonObject
        "array" -> v is JsonArray
        "null" -> v is JsonNull
        "string" -> v is JsonPrimitive && v.isString
        "boolean" -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
        "integer" -> v is JsonPrimitive && !v.isString && v.longOrNull != null
        "number" -> v is JsonPrimitive && !v.isString && v.doubleOrNull != null
        else -> false
    }
}
