package tech.sethi.pebbles.backpack.storage.adapters

import com.google.gson.*
import net.minecraft.item.ItemStack
import net.minecraft.util.collection.DefaultedList
import tech.sethi.pebbles.backpack.api.Backpack
import tech.sethi.pebbles.backpack.api.BackpackTier
import java.lang.reflect.Type
import java.util.UUID

class BackpackTypeAdapter : JsonSerializer<Backpack>, JsonDeserializer<Backpack> {

    private val itemStackAdapter = ItemStackTypeAdapter()

    override fun serialize(backpack: Backpack?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
        if (backpack == null) return JsonNull.INSTANCE

        val json = JsonObject()
        json.addProperty("uuid", backpack.uuid.toString())
        json.addProperty("tier", backpack.tier.name)

        val items = JsonArray()
        backpack.inventory.heldStacks.forEach { item ->
            items.add(itemStackAdapter.serialize(item, ItemStack::class.java, null))
        }
        json.add("items", items)

        return json
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Backpack? {
        if (json is JsonNull) return null

        val jsonObject = json.asJsonObject
        val uuid = UUID.fromString(jsonObject.get("uuid").asString)
        val tier = BackpackTier.valueOf(jsonObject.get("tier").asString)

        val items = DefaultedList.ofSize(tier.size, ItemStack.EMPTY)
        jsonObject.get("items").asJsonArray.forEachIndexed { index, item ->
            items[index] = itemStackAdapter.deserialize(item, ItemStack::class.java, null)
        }

        return Backpack(uuid, tier, items)
    }

}