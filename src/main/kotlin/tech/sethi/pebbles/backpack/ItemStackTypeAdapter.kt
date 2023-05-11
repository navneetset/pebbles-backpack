package tech.sethi.pebbles.backpack

import com.google.gson.*
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.StringNbtReader
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import java.lang.reflect.Type

class ItemStackTypeAdapter : JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
    override fun serialize(src: ItemStack, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("item", Registry.ITEM.getId(src.item).toString())
        jsonObject.addProperty("count", src.count)

        if (src.hasNbt()) {
            jsonObject.addProperty("nbt", src.nbt.toString())
        }

        return jsonObject
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ItemStack {
        val jsonObject = json.asJsonObject
        val item = Registry.ITEM.get(Identifier(jsonObject["item"].asString))
        val count = jsonObject["count"].asInt

        val stack = ItemStack(item, count)

        if (jsonObject.has("nbt")) {
            val nbtString = jsonObject["nbt"].asString
            val nbt = StringNbtReader.parse(nbtString)
            stack.nbt = nbt
        }

        return stack
    }
}

