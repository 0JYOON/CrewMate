package com.example.crewmate.labs.routediagram

import org.json.JSONArray
import org.json.JSONObject

data class DepotPlacement(
    val parent: String,
    val position: String // "before" | "after"
)

data class RailwayLineDef(
    val id: String,
    val name: String,
    val stations: List<String>
)

data class RailwayMasterData(
    val version: Int = 1,
    val updatedAt: String = "",
    val aliases: Map<String, String> = emptyMap(),
    val depots: Map<String, DepotPlacement> = emptyMap(),
    val lines: List<RailwayLineDef> = emptyList()
)

data class MasterSyncResult(
    val success: Boolean,
    val updated: Boolean = false,
    val activeVersion: Int = 1,
    val activeUpdatedAt: String = "",
    val selectedSource: String = "",
    val message: String = ""
)

object RailwayMasterEngine {

    fun parseJson(jsonString: String): RailwayMasterData {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val updatedAt = root.optString("updatedAt", "")

        val aliases = linkedMapOf<String, String>()
        root.optJSONObject("aliases")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                aliases[k] = obj.optString(k, "")
            }
        }

        val depots = linkedMapOf<String, DepotPlacement>()
        root.optJSONObject("depots")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val d = obj.optJSONObject(k) ?: continue
                val parent = d.optString("parent", "")
                val position = d.optString("position", "after")
                if (parent.isNotBlank()) {
                    depots[k] = DepotPlacement(parent = parent, position = position)
                }
            }
        }

        val lines = mutableListOf<RailwayLineDef>()
        root.optJSONArray("lines")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id", "line_$i")
                val name = item.optString("name", id)
                val stnsArr = item.optJSONArray("stations") ?: JSONArray()
                val stns = mutableListOf<String>()
                for (j in 0 until stnsArr.length()) {
                    val s = stnsArr.optString(j, "").trim()
                    if (s.isNotEmpty()) stns.add(s)
                }
                if (stns.isNotEmpty()) {
                    lines.add(RailwayLineDef(id = id, name = name, stations = stns))
                }
            }
        }

        return RailwayMasterData(
            version = version,
            updatedAt = updatedAt,
            aliases = aliases,
            depots = depots,
            lines = lines
        )
    }

    fun toJsonString(data: RailwayMasterData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("updatedAt", data.updatedAt)

        val aliasesObj = JSONObject()
        data.aliases.forEach { (k, v) -> aliasesObj.put(k, v) }
        root.put("aliases", aliasesObj)

        val depotsObj = JSONObject()
        data.depots.forEach { (k, v) ->
            depotsObj.put(
                k,
                JSONObject().apply {
                    put("parent", v.parent)
                    put("position", v.position)
                }
            )
        }
        root.put("depots", depotsObj)

        val linesArr = JSONArray()
        data.lines.forEach { line ->
            linesArr.put(
                JSONObject().apply {
                    put("id", line.id)
                    put("name", line.name)
                    put("stations", JSONArray(line.stations))
                }
            )
        }
        root.put("lines", linesArr)
        return root.toString(2)
    }

    fun merge(base: RailwayMasterData, custom: RailwayMasterData?): RailwayMasterData {
        if (custom == null) return base
        val mergedAliases = base.aliases.toMutableMap().apply { putAll(custom.aliases) }
        val mergedDepots = base.depots.toMutableMap().apply { putAll(custom.depots) }
        val mergedLines = base.lines.toMutableList()
        custom.lines.forEach { customLine ->
            val idx = mergedLines.indexOfFirst { it.id == customLine.id }
            if (idx >= 0) mergedLines[idx] = customLine else mergedLines.add(customLine)
        }
        return base.copy(
            aliases = mergedAliases,
            depots = mergedDepots,
            lines = mergedLines
        )
    }

    fun isNewerThan(candidate: RailwayMasterData, current: RailwayMasterData): Boolean {
        if (candidate.version != current.version) return candidate.version > current.version
        return candidate.updatedAt > current.updatedAt
    }

    fun normalizeStationName(name: String, aliases: Map<String, String>): String {
        if (name.isBlank()) return ""
        var cleaned = name.trim().replace(Regex("\\s*\\(.*?\\)\\s*"), "")
        if (cleaned.length > 2 && cleaned.endsWith("역")) {
            cleaned = cleaned.dropLast(1)
        } else if (cleaned == "서울역") {
            cleaned = "서울"
        }
        return aliases[cleaned] ?: cleaned
    }

    fun findBestMatchingLine(stations: List<String>, master: RailwayMasterData): RailwayLineDef? {
        if (stations.isEmpty()) return null
        val normalizedSet = stations.map { normalizeStationName(it, master.aliases) }.toSet()

        var bestLine: RailwayLineDef? = null
        var bestScore = 0
        for (line in master.lines) {
            val lineSet = line.stations.toSet()
            var score = 0
            for (stn in normalizedSet) {
                if (lineSet.contains(stn)) {
                    score += 2
                } else {
                    val depot = master.depots[stn]
                    if (depot != null && lineSet.contains(normalizeStationName(depot.parent, master.aliases))) {
                        score += 1
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestLine = line
            }
        }
        return if (bestScore > 0) bestLine else null
    }

    fun sortStationsByLineMaster(stations: List<String>, master: RailwayMasterData): List<String> {
        if (stations.size <= 1) return stations
        val bestLine = findBestMatchingLine(stations, master) ?: return stations

        val lineIndexMap = HashMap<String, Int>(bestLine.stations.size)
        bestLine.stations.forEachIndexed { idx, s -> lineIndexMap[s] = idx }

        fun getPositionScore(rawStation: String): Double {
            val norm = normalizeStationName(rawStation, master.aliases)
            lineIndexMap[norm]?.let { return it.toDouble() }
            val depot = master.depots[norm] ?: master.depots[rawStation.trim()]
            if (depot != null) {
                val parentNorm = normalizeStationName(depot.parent, master.aliases)
                val parentIdx = lineIndexMap[parentNorm]
                if (parentIdx != null) {
                    return if (depot.position == "before") parentIdx - 0.5 else parentIdx + 0.5
                }
            }
            return Double.MAX_VALUE
        }

        val indexed = stations.mapIndexed { idx, s -> Triple(s, getPositionScore(s), idx) }
        return indexed
            .sortedWith(compareBy<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }
}
