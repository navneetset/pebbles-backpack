package tech.sethi.pebbles.backpack

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.*
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import java.io.IOException

class PebblesBackpackInitializer : ModInitializer {
    private val logger = LoggerFactory.getLogger("pebbles-backpack")

    override fun onInitialize() {
        logger.info("Registering Pebble's Backpack Commands!")

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            BackpackCommands.register(server.commandManager.dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting { server ->
            val backpacksFile = BackpackCommands.getBackpacksFile(server)
            if (backpacksFile.exists()) {
                BackpackCommands.backpacks.putAll(BackpackCommands.loadBackpacksData(backpacksFile))
            } else {
                try {
                    backpacksFile.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        })


        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, _ ->
            if (world.isClient) {
                return@UseBlockCallback ActionResult.PASS
            }

            val itemStack = player.getStackInHand(hand)

            val skullItem = Registries.ITEM.get(Identifier("minecraft:player_head"))

            if (itemStack.item != skullItem) {
                return@UseBlockCallback ActionResult.PASS
            }

            // check if the head has nbt data "backpackID"
            val backpackID = itemStack.nbt!!.getInt("BackpackID")

            // check if the backpackID is in the backpacks map
            val backpack = BackpackCommands.backpacks[backpackID]

            if (backpack != null) {
                // Open the backpack for the player
                BackpackCommands.openBackpack(player, backpackID)
                return@UseBlockCallback ActionResult.SUCCESS
            }

            return@UseBlockCallback ActionResult.PASS
        })

        // check if player right clicks with a backpack
        val backpackInteraction = let@{ player: PlayerEntity, _: World, _: Hand, stack: ItemStack ->
            val skullItem = Registries.ITEM.get(Identifier("minecraft:player_head"))

            if (stack.item != skullItem) {
                return@let TypedActionResult.pass(stack)
            }

            val backpackID = stack.nbt?.getInt("BackpackID") ?: return@let TypedActionResult.pass(stack)

            val backpack = BackpackCommands.backpacks[backpackID]

            if (backpack != null) {
                BackpackCommands.openBackpack(player, backpackID)
                return@let TypedActionResult.success(stack, true)
            }

            TypedActionResult.pass(stack)
        }


        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            backpackInteraction(player, world, hand, player.getStackInHand(hand))
        })


        logger.info("Pebble's Backpack loaded!")
    }
}
