import com.google.gson.GsonBuilder
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files

interface BcI18nExtension {
    val bondageClubDir: DirectoryProperty
    val skipUpdate: Property<Boolean>
}

val extension = project.extensions.create<BcI18nExtension>("bcI18n")
extension.bondageClubDir.convention(project.layout.projectDirectory.dir("docs/BondageClub"))
extension.skipUpdate.convention(false)

abstract class UpdateBondageClubTask : DefaultTask() {
    @get:Internal
    abstract val bondageClubDir: DirectoryProperty

    @TaskAction
    fun run() {
        val bcDir = bondageClubDir.get().asFile.toPath()
        val realPath = if (Files.isSymbolicLink(bcDir)) Files.readSymbolicLink(bcDir) else bcDir
        var gitRoot = realPath.toAbsolutePath()
        while (gitRoot.parent != null && !Files.exists(gitRoot.resolve(".git"))) {
            gitRoot = gitRoot.parent
        }
        if (!Files.exists(gitRoot.resolve(".git"))) {
            throw GradleException("Could not find git repository for $realPath")
        }
        logger.lifecycle("Updating Bondage-College repository at $gitRoot")
        val process = ProcessBuilder("git", "pull", "--ff-only")
            .directory(gitRoot.toFile())
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("git pull failed with exit code $exitCode")
        }
    }
}

abstract class GenerateI18nTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bondageClubDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @TaskAction
    fun run() {
        val bcDir = bondageClubDir.get().asFile
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        processAssetCsv(bcDir, outDir)

        for (name in listOf("AssetStrings", "ColorGroups", "LayerNames")) {
            processSimpleCsvMerged(
                csvFile = bcDir.resolve("Assets/Female3DCG/$name.csv"),
                outputFile = outDir.resolve("bc/i18n/assets/$name.json")
            )
        }

        processSimpleCsvMerged(
            csvFile = bcDir.resolve("Screens/Character/Preference/ActivityDictionary.csv"),
            outputFile = outDir.resolve("bc/i18n/ActivityDictionary.json")
        )

        processSimpleCsvMerged(
            csvFile = bcDir.resolve("Screens/Interface.csv"),
            outputFile = outDir.resolve("bc/i18n/Interface.json")
        )

        processScreenTexts(bcDir, outDir)

        // Process echo mod directories (convention: sibling to BondageClub dir)
        val docsDir = bcDir.parentFile
        val modNames = listOf("echo-activity-ext", "echo-clothing-ext")
        for (modName in modNames) {
            val modDir = docsDir.resolve(modName)
            if (modDir.exists()) {
                processModDir(modName, modDir, outDir)
            }
        }

        // Generate mod index listing available mods and their file types
        generateModIndex(outDir, modNames)

        val jsonCount = outDir.walk().filter { it.extension == "json" }.count()
        logger.lifecycle("Generated $jsonCount i18n JSON files in $outDir")
    }

    private fun processAssetCsv(bcDir: File, outDir: File) {
        val csvFile = bcDir.resolve("Assets/Female3DCG/Female3DCG.csv")
        if (!csvFile.exists()) {
            logger.warn("Female3DCG.csv not found at $csvFile")
            return
        }

        val assetsOutDir = outDir.resolve("bc/i18n/assets")
        assetsOutDir.mkdirs()

        // Parse 3-column CSV: Group,Name,DisplayName
        val entries = parseCsvLines(csvFile)
        val nested = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val reverseMap = LinkedHashMap<String, MutableList<Pair<String, String>>>()

        for (cols in entries) {
            if (cols.size < 3) continue
            val group = cols[0]
            val name = cols[1]
            val displayName = cols[2]
            nested.getOrPut(group) { LinkedHashMap() }[name] = displayName
            reverseMap.getOrPut(displayName) { mutableListOf() }.add(group to name)
        }

        // Merged: {"": {group: {name: en}}, "CN": {group: {name: 翻译}}, ...}
        val merged = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>()
        merged[""] = nested

        for (txtFile in findTranslationFiles(csvFile.parentFile, "Female3DCG")) {
            val lang = extractLangCode(txtFile.name, "Female3DCG")
            val translated = LinkedHashMap<String, LinkedHashMap<String, String>>()
            for ((group, names) in nested) {
                translated[group] = LinkedHashMap(names)
            }
            applyTranslation(txtFile) { english, translation ->
                val targets = reverseMap[english] ?: return@applyTranslation
                for ((group, name) in targets) {
                    translated[group]?.put(name, translation)
                }
            }
            merged[lang] = translated
        }

        writeJson(assetsOutDir.resolve("Female3DCG.json"), merged)
    }

    private fun processSimpleCsvMerged(csvFile: File, outputFile: File) {
        if (!csvFile.exists()) {
            logger.warn("CSV not found: $csvFile")
            return
        }
        outputFile.parentFile.mkdirs()

        val map = parseSimpleCsvToMap(csvFile)

        // Build reverse map: English value -> keys
        val reverseMap = LinkedHashMap<String, MutableList<String>>()
        for ((key, value) in map) {
            reverseMap.getOrPut(value) { mutableListOf() }.add(key)
        }

        // Merged: {"": {key: en}, "CN": {key: 翻译}, ...}
        val merged = LinkedHashMap<String, LinkedHashMap<String, String>>()
        merged[""] = map

        val baseName = csvFile.nameWithoutExtension
        for (txtFile in findTranslationFiles(csvFile.parentFile, baseName)) {
            val lang = extractLangCode(txtFile.name, baseName)
            val translated = LinkedHashMap(map)
            applyTranslation(txtFile) { english, translation ->
                val keys = reverseMap[english] ?: return@applyTranslation
                for (key in keys) {
                    translated[key] = translation
                }
            }
            merged[lang] = translated
        }

        writeJson(outputFile, merged)
    }

    private fun processScreenTexts(bcDir: File, outDir: File) {
        val screensDir = bcDir.resolve("Screens")
        if (!screensDir.exists()) return

        val screensOutDir = outDir.resolve("bc/i18n/screens")
        screensOutDir.mkdirs()

        screensDir.walk()
            .filter { it.isFile && it.name.startsWith("Text_") && it.name.endsWith(".csv") }
            .forEach { csvFile ->
                val screenName = csvFile.name.removePrefix("Text_").removeSuffix(".csv")
                processSimpleCsvMerged(
                    csvFile = csvFile,
                    outputFile = screensOutDir.resolve("$screenName.json")
                )
            }
    }

    private fun parseCsvLines(file: File): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val content = file.readText(StandardCharsets.UTF_8)
        val reader = BufferedReader(StringReader(content))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line!!
            if (l.isBlank()) continue
            result.add(parseCsvLine(l))
        }
        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        continue
                    }
                } else {
                    sb.append(c)
                }
            } else {
                when (c) {
                    ',' -> {
                        fields.add(sb.toString())
                        sb.clear()
                    }
                    '"' -> inQuotes = true
                    else -> sb.append(c)
                }
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }

    private fun parseSimpleCsvToMap(file: File): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        val lines = parseCsvLines(file)
        for (cols in lines) {
            if (cols.size < 2) continue
            map[cols[0]] = cols[1]
        }
        return map
    }

    private fun findTranslationFiles(dir: File, baseName: String): List<File> {
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("${baseName}_") && it.name.endsWith(".txt") }
            ?.sorted()
            ?: emptyList()
    }

    private fun extractLangCode(fileName: String, baseName: String): String {
        return fileName.removePrefix("${baseName}_").removeSuffix(".txt")
    }

    private fun applyTranslation(
        txtFile: File,
        apply: (english: String, translation: String) -> Unit
    ) {
        val lines = txtFile.readLines(StandardCharsets.UTF_8)
        var i = 0
        while (i + 1 < lines.size) {
            val english = lines[i]
            val translation = lines[i + 1]
            i += 2
            if (english.isBlank() && translation.isBlank()) continue
            apply(english, translation)
        }
    }

    private fun generateModIndex(outDir: File, modNames: List<String>) {
        val modsDir = outDir.resolve("bc/i18n/mods")
        val index = LinkedHashMap<String, List<String>>()
        for (modName in modNames) {
            val modDir = modsDir.resolve(modName)
            if (!modDir.exists()) continue
            val fileTypes = modDir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                ?: continue
            if (fileTypes.isNotEmpty()) {
                index[modName] = fileTypes
            }
        }
        if (index.isNotEmpty()) {
            writeJson(modsDir.resolve("index.json"), index)
        }
    }

    // ========== Mod Processing ==========

    private fun processModDir(modName: String, modDir: File, outDir: File) {
        val componentsDir = modDir.resolve("src/components")
        if (!componentsDir.exists()) {
            logger.warn("Components directory not found in mod: $modDir")
            return
        }

        val jsFiles = componentsDir.walk()
            .filter { it.isFile && it.extension == "js" && it.name != "index.js" }
            .toList()

        if (jsFiles.isEmpty()) return

        val modOutDir = outDir.resolve("bc/i18n/mods/$modName")

        // Collect all activities and assets from all JS files
        val allActivities = mutableListOf<ModActivityData>()
        val allAssets = mutableListOf<ModAssetData>()

        for (jsFile in jsFiles) {
            try {
                val source = stripJsComments(jsFile.readText(StandardCharsets.UTF_8))
                if (source.contains("addCustomActivit")) {
                    allActivities.addAll(extractActivitiesFromJs(source))
                }
                if (source.contains("addAssetWithConfig")) {
                    allAssets.addAll(extractAssetsFromJs(source))
                }
            } catch (e: Exception) {
                logger.warn("Failed to process mod file ${jsFile.relativeTo(modDir)}: ${e.message}")
            }
        }

        if (allActivities.isNotEmpty()) {
            writeModActivities(allActivities, modOutDir)
        }
        if (allAssets.isNotEmpty()) {
            writeModAssets(allAssets, modOutDir)
        }

        val modJsonCount = modOutDir.walk().filter { it.extension == "json" }.count()
        if (modJsonCount > 0) {
            logger.lifecycle("  Mod '$modName': $modJsonCount JSON files, ${allActivities.size} activities, ${allAssets.size} assets")
        }
    }

    // ========== Activity Extraction ==========

    private fun extractActivitiesFromJs(source: String): List<ModActivityData> {
        val results = mutableListOf<ModActivityData>()

        // Find direct activity objects: { activity: { Name: "..." }, label: { ... }, dialog: { ... } }
        // by scanning for activity: { Name: " patterns
        val activityPattern = Regex("""activity\s*:\s*\{""")
        for (actMatch in activityPattern.findAll(source)) {
            // Find the enclosing object (go back to find the opening brace)
            val enclosingStart = findEnclosingObjectStart(source, actMatch.range.first)
            if (enclosingStart < 0) continue
            val enclosingEnd = findMatchingClose(source, enclosingStart)
            if (enclosingEnd < 0) continue
            val objStr = source.substring(enclosingStart, enclosingEnd + 1)

            // Extract activity Name
            val nameMatch = Regex("""Name\s*:\s*(?:"([^"]*)"|'([^']*)'|`([^`]*)`)""").find(objStr) ?: continue
            val name = nameMatch.groupValues[1].ifEmpty { nameMatch.groupValues[2].ifEmpty { nameMatch.groupValues[3] } }
            if (name.isEmpty()) continue

            // Extract translation fields
            val label = extractFieldAsTranslation(objStr, "label")
            val labelSelf = extractFieldAsTranslation(objStr, "labelSelf")
            val dialog = extractFieldAsTranslation(objStr, "dialog")
            val dialogSelf = extractFieldAsTranslation(objStr, "dialogSelf")

            if (label != null || labelSelf != null || dialog != null || dialogSelf != null) {
                results.add(ModActivityData(name, label, labelSelf, dialog, dialogSelf))
            }
        }

        // Find template activities: fromTemplateActivity(...)
        val templatePattern = Regex("""fromTemplateActivity\s*\(""")
        for (tmplMatch in templatePattern.findAll(source)) {
            val parenStart = source.indexOf('(', tmplMatch.range.first)
            if (parenStart < 0) continue
            val parenEnd = findMatchingClose(source, parenStart)
            if (parenEnd < 0) continue
            results.addAll(parseTemplateActivity(source.substring(parenStart + 1, parenEnd)))
        }

        return results
    }

    private fun parseTemplateActivity(argsContent: String): List<ModActivityData> {
        val results = mutableListOf<ModActivityData>()
        val args = splitTopLevelElements(argsContent)
        if (args.size < 3) return results

        // Arg 2: group translations { LANG: { groupName: "text" } }
        val groupTrans = parseJsNestedObject(args[1].trim())

        // Arg 3: template labels/dialogs { label: {...}, dialog: {...} }
        val templateArg = args[2].trim()
        val templateLabel = extractFieldAsTranslation(templateArg, "label")
        val templateLabelSelf = extractFieldAsTranslation(templateArg, "labelSelf")
        val templateDialog = extractFieldAsTranslation(templateArg, "dialog")
        val templateDialogSelf = extractFieldAsTranslation(templateArg, "dialogSelf")

        // Arg 1: activity definitions - extract Names and Targets
        val activitiesArg = args[0].trim()
        val namePattern = Regex("""Name\s*:\s*(?:"([^"]*)"|'([^']*)'|`([^`]*)`)""")
        val targetPattern = Regex("""Target\s*:\s*\[""")

        for (nameMatch in namePattern.findAll(activitiesArg)) {
            val actName = nameMatch.groupValues[1].ifEmpty {
                nameMatch.groupValues[2].ifEmpty { nameMatch.groupValues[3] }
            }
            if (actName.isEmpty()) continue

            // Find Target array near this Name
            val searchStart = maxOf(0, nameMatch.range.first - 100)
            val searchEnd = minOf(activitiesArg.length, nameMatch.range.last + 300)
            val nearbyText = activitiesArg.substring(searchStart, searchEnd)
            val targetMatch = targetPattern.find(nearbyText)
            val targetGroups = if (targetMatch != null) {
                val bracketPos = nearbyText.indexOf('[', targetMatch.range.first)
                val bracketEnd = findMatchingClose(nearbyText, bracketPos)
                if (bracketEnd >= 0) {
                    val targetBlock = nearbyText.substring(bracketPos + 1, bracketEnd)
                    Regex(""""([^"]*)"""").findAll(targetBlock).map { it.groupValues[1] }.toList()
                } else emptyList()
            } else emptyList()

            // Expand template with first target group
            val firstTarget = targetGroups.firstOrNull()
            fun expandTemplate(template: Map<String, String>?): Map<String, String>? {
                if (template == null || firstTarget == null) return template
                return template.mapValues { (lang, text) ->
                    val groupName = groupTrans[lang]?.get(firstTarget) ?: firstTarget
                    text.replace("\$group", groupName)
                }
            }

            results.add(
                ModActivityData(
                    actName,
                    expandTemplate(templateLabel),
                    expandTemplate(templateLabelSelf),
                    expandTemplate(templateDialog),
                    expandTemplate(templateDialogSelf)
                )
            )
        }

        return results
    }

    // ========== Clothing/Asset Extraction ==========

    private fun extractAssetsFromJs(source: String): List<ModAssetData> {
        val results = mutableListOf<ModAssetData>()

        // Find asset Names
        val assetNames = mutableListOf<String>()
        val namePattern = Regex("""Name\s*:\s*"([^"]*)"[^}]*?(?:Random|Gender|Top|Left|Layer|Priority|DynamicGroupName)""")
        for (m in namePattern.findAll(source)) {
            assetNames.add(m.groupValues[1])
        }
        // Fallback: if no names found with asset-like context, try simpler pattern
        if (assetNames.isEmpty()) {
            val simpleNamePattern = Regex("""Name\s*:\s*"([^"]+)"(?=\s*,)""")
            for (m in simpleNamePattern.findAll(source)) {
                val name = m.groupValues[1]
                // Skip names that look like layer or module names (very short or reserved)
                if (name.length > 1 && !name.matches(Regex("[A-Z]\\d?"))) {
                    assetNames.add(name)
                    break
                }
            }
        }

        // Find groups from addAssetWithConfig calls
        val groups = mutableListOf<String>()
        val addCallPattern = Regex("""addAssetWithConfig\s*\(""")
        for (callMatch in addCallPattern.findAll(source)) {
            val afterParenStart = callMatch.range.last + 1 // skip past '('
            val afterParen = source.substring(afterParenStart)
            // Try string literal: addAssetWithConfig("GroupName", ...)
            val stringGroup = Regex("""^\s*"([^"]+)"""").find(afterParen)
            if (stringGroup != null) {
                groups.add(stringGroup.groupValues[1])
                continue
            }
            // Try array literal: addAssetWithConfig(["Group1", "Group2"], ...)
            val arrayGroup = Regex("""^\s*\[""").find(afterParen)
            if (arrayGroup != null) {
                val bracketStart = afterParenStart + afterParen.indexOf('[')
                val bracketEnd = findMatchingClose(source, bracketStart)
                if (bracketEnd >= 0) {
                    val arrayBlock = source.substring(bracketStart + 1, bracketEnd)
                    Regex(""""([^"]*)"""").findAll(arrayBlock).forEach { groups.add(it.groupValues[1]) }
                }
                continue
            }
            // Try spread: addAssetWithConfig(...assets) where assets = ["GroupName", ...]
            val spreadMatch = Regex("""^\s*\.\.\.(\w+)""").find(afterParen)
            if (spreadMatch != null) {
                val varName = spreadMatch.groupValues[1]
                val constPattern = Regex("""(?:const|let|var)\s+${Regex.escape(varName)}\s*=\s*\[""")
                val constMatch = constPattern.find(source)
                if (constMatch != null) {
                    val bracketPos = source.indexOf('[', constMatch.range.first)
                    val bracketEnd = findMatchingClose(source, bracketPos)
                    if (bracketEnd >= 0) {
                        val firstStr = Regex(""""([^"]*)"""").find(source.substring(bracketPos + 1, bracketEnd))
                        if (firstStr != null) groups.add(firstStr.groupValues[1])
                    }
                }
            }
        }

        // Extract translation objects (both const declarations and inline)
        val translation = extractConstOrFieldTranslation(source, "translation")
        val layerNames = extractConstOrFieldNestedTranslation(source, "layerNames")
        val assetStrings = extractConstOrFieldNestedTranslation(source, "assetStrings")

        if (translation != null || layerNames != null || assetStrings != null) {
            val assetName = assetNames.firstOrNull() ?: "Unknown"
            val assetGroups = if (groups.isNotEmpty()) groups else listOf("Unknown")
            results.add(ModAssetData(assetGroups, assetName, translation, layerNames, assetStrings))
        }

        return results
    }

    private fun extractConstOrFieldTranslation(source: String, fieldName: String): Map<String, String>? {
        // Try const declaration: const fieldName = { ... }
        val constPattern = Regex("""(?:const|let|var)\s+$fieldName\s*=\s*\{""")
        val constMatch = constPattern.find(source)
        if (constMatch != null) {
            val bracePos = source.indexOf('{', constMatch.range.first + fieldName.length)
            if (bracePos >= 0) {
                val closePos = findMatchingClose(source, bracePos)
                if (closePos >= 0) {
                    val block = source.substring(bracePos, closePos + 1)
                    val parsed = parseJsFlatObject(block)
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        }
        // Try inline field: fieldName: { ... }
        return extractFieldAsTranslation(source, fieldName)
    }

    private fun extractConstOrFieldNestedTranslation(
        source: String,
        fieldName: String
    ): Map<String, Map<String, String>>? {
        // Try const declaration: const fieldName = { ... }
        val constPattern = Regex("""(?:const|let|var)\s+$fieldName\s*=\s*\{""")
        val constMatch = constPattern.find(source)
        if (constMatch != null) {
            val bracePos = source.indexOf('{', constMatch.range.first + fieldName.length)
            if (bracePos >= 0) {
                val closePos = findMatchingClose(source, bracePos)
                if (closePos >= 0) {
                    val block = source.substring(bracePos, closePos + 1)
                    val parsed = parseJsNestedObject(block)
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        }
        // Try inline field: fieldName: { ... }
        return extractFieldAsNestedTranslation(source, fieldName)
    }

    // ========== Mod Output Writers ==========

    private fun writeModActivities(activities: List<ModActivityData>, outDir: File) {
        // Collect all language codes
        val langs = mutableSetOf<String>()
        for (act in activities) {
            for (field in listOf(act.label, act.labelSelf, act.dialog, act.dialogSelf)) {
                if (field != null) langs.addAll(field.keys)
            }
        }

        // Build output: { "": { name: { field: text } }, "CN": { ... } }
        val merged = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>()
        for (lang in listOf("EN") + (langs - "EN").sorted()) {
            val outLang = if (lang == "EN") "" else lang
            val langMap = LinkedHashMap<String, LinkedHashMap<String, String>>()
            for (act in activities) {
                val fields = LinkedHashMap<String, String>()
                act.label?.get(lang)?.let { fields["label"] = it }
                act.labelSelf?.get(lang)?.let { fields["labelSelf"] = it }
                act.dialog?.get(lang)?.let { fields["dialog"] = it }
                act.dialogSelf?.get(lang)?.let { fields["dialogSelf"] = it }
                if (fields.isNotEmpty()) {
                    langMap[act.name] = fields
                }
            }
            if (langMap.isNotEmpty()) {
                merged[outLang] = langMap
            }
        }

        if (merged.isNotEmpty()) {
            writeJson(outDir.resolve("activities.json"), merged)
        }
    }

    private fun writeModAssets(assets: List<ModAssetData>, outDir: File) {
        // Collect all language codes
        val langs = mutableSetOf<String>()
        for (asset in assets) {
            asset.translation?.keys?.let { langs.addAll(it) }
            asset.layerNames?.keys?.let { langs.addAll(it) }
            asset.assetStrings?.keys?.let { langs.addAll(it) }
        }

        val orderedLangs = listOf("EN") + (langs - "EN").sorted()

        // 1. Asset display names (like Female3DCG.json): { "": { group: { name: displayName } } }
        val assetsMerged = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>()
        for (lang in orderedLangs) {
            val outLang = if (lang == "EN") "" else lang
            val langMap = LinkedHashMap<String, LinkedHashMap<String, String>>()
            for (asset in assets) {
                val displayName = asset.translation?.get(lang) ?: continue
                for (group in asset.groups) {
                    langMap.getOrPut(group) { LinkedHashMap() }[asset.name] = displayName
                }
            }
            if (langMap.isNotEmpty()) {
                assetsMerged[outLang] = langMap
            }
        }
        if (assetsMerged.isNotEmpty()) {
            writeJson(outDir.resolve("assets.json"), assetsMerged)
        }

        // 2. Layer names: { "": { "assetName/layerName": displayName } }
        val layersMerged = LinkedHashMap<String, LinkedHashMap<String, String>>()
        for (lang in orderedLangs) {
            val outLang = if (lang == "EN") "" else lang
            val langMap = LinkedHashMap<String, String>()
            for (asset in assets) {
                val layers = asset.layerNames?.get(lang) ?: continue
                for ((layerName, displayName) in layers) {
                    langMap["${asset.name}/$layerName"] = displayName
                }
            }
            if (langMap.isNotEmpty()) {
                layersMerged[outLang] = langMap
            }
        }
        if (layersMerged.isNotEmpty()) {
            writeJson(outDir.resolve("layerNames.json"), layersMerged)
        }

        // 3. Asset strings: { "": { "assetName/key": text } }
        val stringsMerged = LinkedHashMap<String, LinkedHashMap<String, String>>()
        for (lang in orderedLangs) {
            val outLang = if (lang == "EN") "" else lang
            val langMap = LinkedHashMap<String, String>()
            for (asset in assets) {
                val strings = asset.assetStrings?.get(lang) ?: continue
                for ((key, text) in strings) {
                    langMap["${asset.name}/$key"] = text
                }
            }
            if (langMap.isNotEmpty()) {
                stringsMerged[outLang] = langMap
            }
        }
        if (stringsMerged.isNotEmpty()) {
            writeJson(outDir.resolve("assetStrings.json"), stringsMerged)
        }
    }

    // ========== JS Parsing Utilities ==========

    private fun stripJsComments(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            // Handle string literals - preserve them as-is
            if (c == '"' || c == '\'' || c == '`') {
                val start = i
                i++
                while (i < source.length) {
                    if (source[i] == '\\') { i += 2; continue }
                    if (source[i] == c) { i++; break }
                    i++
                }
                sb.append(source, start, i)
                continue
            }
            // Handle line comments
            if (c == '/' && i + 1 < source.length && source[i + 1] == '/') {
                val end = source.indexOf('\n', i)
                i = if (end >= 0) end else source.length
                continue
            }
            // Handle block comments
            if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                val end = source.indexOf("*/", i + 2)
                i = if (end >= 0) end + 2 else source.length
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun findMatchingClose(content: String, openPos: Int): Int {
        if (openPos < 0 || openPos >= content.length) return -1
        val openChar = content[openPos]
        val closeChar = when (openChar) {
            '{' -> '}'
            '[' -> ']'
            '(' -> ')'
            else -> return -1
        }
        var depth = 0
        var i = openPos
        while (i < content.length) {
            val c = content[i]
            if (c == '"' || c == '\'' || c == '`') {
                i++
                while (i < content.length) {
                    if (content[i] == '\\') { i += 2; continue }
                    if (content[i] == c) { i++; break }
                    i++
                }
                continue
            }
            if (c == openChar) depth++
            else if (c == closeChar) {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun findEnclosingObjectStart(content: String, pos: Int): Int {
        // Walk backwards from pos to find the opening { of the enclosing object
        var depth = 0
        var i = pos - 1
        while (i >= 0) {
            val c = content[i]
            // Simple backwards scan - skip string contents
            when (c) {
                '}', ']', ')' -> depth++
                '{' -> {
                    if (depth == 0) return i
                    depth--
                }
                '[' -> {
                    if (depth == 0) return -1
                    depth--
                }
                '(' -> {
                    if (depth == 0) return -1
                    depth--
                }
            }
            i--
        }
        return -1
    }

    private fun splitTopLevelElements(content: String): List<String> {
        val elements = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '"' || c == '\'' || c == '`') {
                i++
                while (i < content.length) {
                    if (content[i] == '\\') { i += 2; continue }
                    if (content[i] == c) { i++; break }
                    i++
                }
                continue
            }
            when (c) {
                '{', '[', '(' -> depth++
                '}', ']', ')' -> depth--
                ',' -> if (depth == 0) {
                    val element = content.substring(start, i).trim()
                    if (element.isNotEmpty()) elements.add(element)
                    start = i + 1
                }
            }
            i++
        }
        val last = content.substring(start).trim()
        if (last.isNotEmpty()) elements.add(last)
        return elements
    }

    private fun extractFieldAsTranslation(source: String, fieldName: String): Map<String, String>? {
        val pattern = Regex("""(?:^|[\s,{(])$fieldName\s*:\s*\{""")
        val match = pattern.find(source) ?: return null
        val bracePos = source.indexOf('{', match.range.last - 1)
        if (bracePos < 0) return null
        val closePos = findMatchingClose(source, bracePos)
        if (closePos < 0) return null
        val block = source.substring(bracePos, closePos + 1)
        // Only accept if it looks like a translation object (has language code keys)
        val parsed = parseJsFlatObject(block)
        val hasLangKeys = parsed.keys.any { it in KNOWN_LANG_CODES }
        return if (hasLangKeys) parsed else null
    }

    private fun extractFieldAsNestedTranslation(
        source: String,
        fieldName: String
    ): Map<String, Map<String, String>>? {
        val pattern = Regex("""(?:^|[\s,{(])$fieldName\s*:\s*\{""")
        val match = pattern.find(source) ?: return null
        val bracePos = source.indexOf('{', match.range.last - 1)
        if (bracePos < 0) return null
        val closePos = findMatchingClose(source, bracePos)
        if (closePos < 0) return null
        val block = source.substring(bracePos, closePos + 1)
        val parsed = parseJsNestedObject(block)
        val hasLangKeys = parsed.keys.any { it in KNOWN_LANG_CODES }
        return if (hasLangKeys) parsed else null
    }

    private fun parseJsFlatObject(block: String): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        val regex = Regex("""([\w\u4e00-\u9fff\u0400-\u04ff]+)\s*:\s*(?:"((?:[^"\\]|\\.)*)"|'((?:[^'\\]|\\.)*)')""")
        for (match in regex.findAll(block)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            map[key] = unescapeJsString(value)
        }
        return map
    }

    private fun parseJsNestedObject(
        block: String
    ): LinkedHashMap<String, LinkedHashMap<String, String>> {
        val map = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val keyPattern = Regex("""(\w+)\s*:\s*\{""")
        var searchFrom = 1 // skip opening brace
        while (searchFrom < block.length) {
            val match = keyPattern.find(block, searchFrom) ?: break
            val key = match.groupValues[1]
            val braceStart = block.indexOf('{', match.range.last - 1)
            if (braceStart < 0) break
            val braceEnd = findMatchingClose(block, braceStart)
            if (braceEnd < 0) break
            val innerBlock = block.substring(braceStart, braceEnd + 1)
            // Check if inner values are strings (flat) or objects (skip deeper nesting)
            val innerParsed = parseJsFlatObject(innerBlock)
            if (innerParsed.isNotEmpty()) {
                map[key] = innerParsed
            }
            searchFrom = braceEnd + 1
        }
        return map
    }

    private fun unescapeJsString(s: String): String {
        return s.replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    companion object {
        private val KNOWN_LANG_CODES = setOf("CN", "EN", "RU", "UA", "TW", "DE", "FR", "ES", "PT", "JP", "KR")
    }

    private fun writeJson(file: File, data: Any) {
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(data), StandardCharsets.UTF_8)
    }
}

private data class ModActivityData(
    val name: String,
    val label: Map<String, String>? = null,
    val labelSelf: Map<String, String>? = null,
    val dialog: Map<String, String>? = null,
    val dialogSelf: Map<String, String>? = null,
)

private data class ModAssetData(
    val groups: List<String>,
    val name: String,
    val translation: Map<String, String>? = null,
    val layerNames: Map<String, Map<String, String>>? = null,
    val assetStrings: Map<String, Map<String, String>>? = null,
)

val updateBondageClub = tasks.register<UpdateBondageClubTask>("updateBondageClub") {
    group = "bc"
    bondageClubDir.set(extension.bondageClubDir)
}

val generateI18n = tasks.register<GenerateI18nTask>("generateI18n") {
    group = "bc"
    bondageClubDir.set(extension.bondageClubDir)
    outputDir.set(project.layout.buildDirectory.dir("generated/resources/i18n"))

    if (!extension.skipUpdate.get()) {
        dependsOn(updateBondageClub)
    }
}

the<JavaPluginExtension>().sourceSets.named("main") {
    resources.srcDir(generateI18n.flatMap { it.outputDir })
}
