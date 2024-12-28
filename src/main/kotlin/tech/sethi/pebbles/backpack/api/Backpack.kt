package tech.sethi.pebbles.backpack.api

import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.datafixer.TypeReferences
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringNbtReader
import net.minecraft.util.collection.DefaultedList
import tech.sethi.pebbles.backpack.PebblesBackpackInitializer.server
import java.util.UUID

class Backpack(val uuid: UUID = UUID.randomUUID(), val tier: BackpackTier, items: DefaultedList<ItemStack>) {

    @Transient
    val inventory = SimpleInventory(tier.size)

    init {
        items.forEachIndexed { index, itemStack ->
            inventory.setStack(index, itemStack)
        }
    }

    fun toItemStack(): ItemStack {
        var item = ItemStack(Items.PLAYER_HEAD)

        val legacyNbt = NbtCompound().apply {
            putString("id", item.registryEntry.idAsString)
            putInt("Count", 1)
            put("tag", StringNbtReader.parse(tier.nbt))
        }
        val updatedNbt = server?.dataFixer?.update(
            TypeReferences.ITEM_STACK,
            Dynamic(server!!.registryManager.getOps(NbtOps.INSTANCE), legacyNbt),
            3700,
            SharedConstants.getGameVersion().saveVersion.id
        )?.value

        item = ItemStack.CODEC.parse(server!!.registryManager.getOps(NbtOps.INSTANCE), updatedNbt).result()
            .orElse(ItemStack.EMPTY)

        item.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(NbtCompound().apply {
            putUuid("BackpackUUID", uuid)
        }))

        return item
    }

}