package com.agentchat.lite.agent.tools

import com.agentchat.lite.agent.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull

/**
 * 单位换算：长度 / 重量 / 温度。
 *
 * 长度与重量通过"到基础单位的系数"做乘法换算；温度有非线性变换，单独处理。
 */
class UnitConverterTool : Tool {

    override val name: String = "unit_converter"

    override val description: String = "单位换算：长度（米/千米/英寸/英尺/英里等）、重量（千克/磅/盎司等）、温度（摄氏度/华氏度/开尔文）"

    override val jsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "value": {"type": "number", "description": "要换算的数值"},
            "from": {"type": "string", "description": "源单位，如 米/英寸/磅/摄氏度"},
            "to": {"type": "string", "description": "目标单位，如 英尺/千克/华氏度"}
          },
          "required": ["value", "from", "to"]
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    // 长度：全部换算到米的系数
    private val lengthToMeter = mapOf(
        "毫米" to 0.001, "厘米" to 0.01, "米" to 1.0, "千米" to 1000.0,
        "毫米mm" to 0.001, "厘米cm" to 0.01, "米m" to 1.0, "千米km" to 1000.0,
        "英寸" to 0.0254, "英尺" to 0.3048, "码" to 0.9144, "英里" to 1609.344,
    )

    // 重量：全部换算到千克的系数
    private val weightToKg = mapOf(
        "毫克" to 0.000001, "克" to 0.001, "千克" to 1.0, "吨" to 1000.0,
        "磅" to 0.45359237, "盎司" to 0.0283495231,
    )

    private val temperatureUnits = setOf("摄氏度", "华氏度", "开尔文", "℃", "℉", "K")

    override suspend fun execute(arguments: String): String {
        val args = runCatching {
            json.parseToJsonElement(arguments) as? JsonObject
        }.getOrNull() ?: return "参数解析失败"

        val value = (args["value"] as? JsonPrimitive)?.doubleOrNull
            ?: return "缺少或非法的 value 参数"
        val from = (args["from"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val to = (args["to"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (from.isEmpty() || to.isEmpty()) return "缺少 from 或 to 参数"

        // 温度
        if (from in temperatureUnits || to in temperatureUnits) {
            val celsius = toCelsius(from, value) ?: return "不支持的温度单位: $from"
            val result = fromCelsius(to, celsius) ?: return "不支持的温度单位: $to"
            return format(value, from, result, to)
        }

        // 长度
        lengthToMeter[from]?.let { f ->
            lengthToMeter[to]?.let { t ->
                val meters = value * f
                return format(value, from, meters / t, to)
            }
        }
        // 重量
        weightToKg[from]?.let { f ->
            weightToKg[to]?.let { t ->
                val kg = value * f
                return format(value, from, kg / t, to)
            }
        }

        return "不支持的单位组合: $from → $to"
    }

    private fun toCelsius(unit: String, v: Double): Double? = when (unit) {
        "摄氏度", "℃" -> v
        "华氏度", "℉" -> (v - 32) * 5 / 9
        "开尔文", "K" -> v - 273.15
        else -> null
    }

    private fun fromCelsius(unit: String, c: Double): Double? = when (unit) {
        "摄氏度", "℃" -> c
        "华氏度", "℉" -> c * 9 / 5 + 32
        "开尔文", "K" -> c + 273.15
        else -> null
    }

    private fun format(v: Double, from: String, result: Double, to: String): String {
        val rounded = Math.round(result * 10000.0) / 10000.0
        return "$v $from = $rounded $to"
    }
}
