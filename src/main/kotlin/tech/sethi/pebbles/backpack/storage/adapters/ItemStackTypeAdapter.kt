package tech.sethi.pebbles.backpack.storage.adapters

import com.google.gson.*
import com.mojang.serialization.Dynamic
import kotlinx.serialization.json.Json
import net.minecraft.SharedConstants
import net.minecraft.component.ComponentChanges
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import tech.sethi.pebbles.backpack.PebblesBackpackInitializer
import tech.sethi.pebbles.backpack.PebblesBackpackInitializer.server
import java.lang.reflect.Type
import kotlin.jvm.optionals.getOrNull

class ItemStackTypeAdapter : JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
    override fun serialize(src: ItemStack?, typeOfSrc: Type, context: JsonSerializationContext?): JsonElement {
        if (src == null) return JsonNull.INSTANCE

        val jsonObject = JsonObject()
        jsonObject.addProperty("item", Registries.ITEM.getId(src.item).toString())
        jsonObject.addProperty("count", src.count)
        jsonObject.addProperty("dataVersion", SharedConstants.getGameVersion().saveVersion.id)

        if (src.componentChanges.size() > 0) {
            val nbt =
                ComponentChanges.CODEC.encodeStart(PebblesBackpackInitializer.nbtOps, src.componentChanges).result()
                    .orElse(null)
            if (nbt != null) jsonObject.addProperty("nbt", nbt.toString())
        }

        return jsonObject
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext?): ItemStack? {
        if (json is JsonNull) return null

        val jsonObject = json.asJsonObject
        val item = Registries.ITEM.get(Identifier.of(jsonObject["item"].asString))
        val count = jsonObject["count"].asInt

        var stack = ItemStack(item, count)

        if (jsonObject.has("dataVersion") && jsonObject.has("nbt")) {
            val nbt = ComponentChanges.CODEC.parse(
                PebblesBackpackInitializer.nbtOps, StringNbtReader.parse(jsonObject["nbt"].asString)
            ).result().orElse(null)
            stack.applyChanges(nbt)
        } else if (jsonObject.has("nbt")) {
            val nbtString = jsonObject["nbt"].asString
            val legacyNbt = NbtCompound().apply {
                putString("id", stack.registryEntry.idAsString)
                putInt("Count", count)
                put("tag", StringNbtReader.parse(nbtString))
            }

            val updatedNbt = server!!.dataFixer?.update(
                TypeReferences.ITEM_STACK,
                Dynamic(PebblesBackpackInitializer.nbtOps, legacyNbt),
                3700,
                SharedConstants.getGameVersion().saveVersion.id
            )?.value

            stack =
                ItemStack.CODEC.parse(PebblesBackpackInitializer.nbtOps, updatedNbt).result().orElse(ItemStack.EMPTY)
        }

        return stack
    }
}

