package tech.sethi.pebbles.backpack.inventory

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.Generic3x3ContainerScreenHandler
import net.minecraft.screen.slot.SlotActionType
import tech.sethi.pebbles.backpack.debounce.debounce
import tech.sethi.pebbles.backpack.migration.LegacyMigration
import tech.sethi.pebbles.backpack.storage.BackpackCache
import java.util.*
import kotlin.time.Duration.Companion.seconds

class LeatherBackpackScreenHandler(
    syncId: Int,
    private val backpackUUID: UUID,
    private val playerInventory: PlayerInventory,
    backpackInventory: Inventory
) : Generic3x3ContainerScreenHandler(syncId, playerInventory, backpackInventory) {

    private val save = debounce(time = 5.seconds, key = backpackUUID) {
        BackpackCache.saveAsync(backpackUUID)
    }

    override fun insertItem(stack: ItemStack, startIndex: Int, endIndex: Int, fromLast: Boolean): Boolean {
        if (isBackpack(stack)) return false
        return super.insertItem(stack, startIndex, endIndex, fromLast)
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity) {
        if (slotIndex < 0) {
            super.onSlotClick(slotIndex, button, actionType, player)
            if (!player.world.isClient) {
                save()
            }
            return
        }

        val slot = this.slots[slotIndex]

        // Prevent picking up or interacting with backpacks in slots
        if (isBackpack(slot.stack)) return

        // Prevent placing backpacks via cursor
        if (isBackpack(cursorStack)) {
            // Only block if targeting backpack inventory slots (not player inventory)
            // 3x3 container has 9 backpack slots (indices 0-8)
            if (slotIndex < 9) return
        }

        // Prevent swapping backpacks using number keys
        if (actionType == SlotActionType.SWAP) {
            val hotbarStack = player.inventory.getStack(button)
            if (isBackpack(hotbarStack)) return
        }

        super.onSlotClick(slotIndex, button, actionType, player)
        if (!player.world.isClient) {
            save()
        }
    }

    private fun isBackpack(itemStack: ItemStack): Boolean {
        return itemStack.item == Items.PLAYER_HEAD && LegacyMigration.isBackpack(itemStack)
    }

    override fun onContentChanged(inventory: Inventory?) {
        super.onContentChanged(inventory)
        val player = playerInventory.player
        if (!player.world.isClient) {
            save()
        }
    }

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        if (!player.world.isClient) {
            save()
        }
    }

}