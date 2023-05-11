package tech.sethi.pebbles.backpack

import net.minecraft.item.ItemStack

data class BackpackData(val id: Int, val size: Int, val items: List<ItemStack>, var playerName: String? = null)

fun BackpackInventory.toData(id: Int): BackpackData {
    val items = mutableListOf<ItemStack>()
    for (i in 0 until this.size()) {
        items.add(this.getStack(i))
    }
    return BackpackData(id, this.size(), items, this.playerName)
}
