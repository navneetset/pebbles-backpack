package tech.sethi.pebbles.backpack.migration

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import net.minecraft.util.collection.DefaultedList
import tech.sethi.pebbles.backpack.api.Backpack
import tech.sethi.pebbles.backpack.api.BackpackTier
import tech.sethi.pebbles.backpack.storage.BackpackCache
import tech.sethi.pebbles.backpack.storage.adapters.ItemStackTypeAdapter
import java.io.File
import java.util.UUID

object LegacyMigration {

    private val legacyGson = GsonBuilder()
        .registerTypeAdapter(ItemStack::class.java, ItemStackTypeAdapter())
        .create()

    fun isBackpack(maybeBackpack: ItemStack): Boolean {
        if (isLegacyBackpack(maybeBackpack)) return true

        if (maybeBackpack.componentChanges.size() < 1) return false
        return maybeBackpack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.containsUuid("BackpackUUID") ?: false
    }

    fun migrateItemStack(backpack: ItemStack) {
        if (!isLegacyBackpack(backpack)) return

        val nbt = backpack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: return
        val legacyId = nbt.getInt("BackpackID")
        val newId = UUID(0L, legacyId.toLong())
        nbt.remove("BackpackID")
        nbt.putUuid("BackpackUUID", newId)
    }

    private fun isLegacyBackpack(backpack: ItemStack): Boolean {
        if (backpack.componentChanges.size() < 1) return false
        return backpack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.contains("BackpackID") ?: false
    }

    fun migrateLegacyBackpacks(server: MinecraftServer) {
        val worldDir = server.getSavePath(WorldSavePath.ROOT).toFile()
        val legacySaveFile = File(worldDir, "pebbles_backpacks.json")
        if (!legacySaveFile.exists()) return

        val backupDestination = File(worldDir, "pebbles_backpacks.backup.json")

        backupLegacyFile(legacySaveFile, backupDestination)

        legacySaveFile.reader().use { reader ->
            val jsonElement = JsonParser.parseReader(reader)
            var backpackDataArray = JsonArray()
            if (!jsonElement.isJsonNull) {
                backpackDataArray = jsonElement.asJsonArray
            }

            for (backpackDataJson in backpackDataArray) {
                val backpackData = legacyGson.fromJson(backpackDataJson, BackpackData::class.java)
                val uuid = UUID(0L, backpackData.id.toLong())
                val tier = getBackpackTierFromSize(backpackData.size)
                val items = DefaultedList.ofSize(tier.size, ItemStack.EMPTY)
                for (i in 0..backpackData.items.size) {
                    if (i < tier.size) items[i] = backpackData.items[i]
                }

                val backpack = Backpack(uuid, tier, items)
                BackpackCache[uuid] = backpack
                BackpackCache.save(uuid)
            }
        }

        legacySaveFile.delete()
    }

    private fun backupLegacyFile(original: File, destination: File) {
        if (!original.exists()) return

        val contents = original.readText()
        if (!destination.exists()) destination.createNewFile()
        destination.writeText(contents)
    }

    private fun getBackpackTierFromSize(size: Int): BackpackTier {
        return BackpackTier.values().firstOrNull { it.size == size } ?: BackpackTier.Copper
    }

}