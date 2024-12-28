package tech.sethi.pebbles.backpack

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.block.Blocks
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.util.*
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.backpack.inventory.InventoryHandler
import tech.sethi.pebbles.backpack.migration.LegacyMigration
import tech.sethi.pebbles.backpack.storage.BackpackCache
import java.io.File

object PebblesBackpackInitializer : ModInitializer {

    val MODID = "pebbles-backpack"
    val LOGGER = LoggerFactory.getLogger(MODID)
    var server: MinecraftServer? = null
    var nbtOps: RegistryOps<NbtElement>? = null

    override fun onInitialize() {
        LOGGER.info("Registering Pebble's Backpack Commands!")

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            BackpackCommands.register(server.commandManager.dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting { server ->
            this.server = server
            nbtOps = server!!.registryManager.getOps(NbtOps.INSTANCE)
            BackpackCache.initialize(getOrCreateRootBackpackFolder(server))
            LegacyMigration.migrateLegacyBackpacks(server)
        })


        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hit ->
            if (!player.isSneaking && !shouldOpenBackpack(world, hit)) {
                // We fail on sneak to prevent player from placing backpack
                return@UseBlockCallback ActionResult.PASS
            }

            val result = handleBackpackInteraction(player, world, hand)
            return@UseBlockCallback if (result) ActionResult.SUCCESS else ActionResult.PASS
        })

        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            handleBackpackInteraction(player, world, hand)
            val stack = player.getStackInHand(hand)
            return@UseItemCallback TypedActionResult.pass(stack)
        })


        LOGGER.info("Pebble's Backpack loaded!")
    }

    private fun shouldOpenBackpack(world: World, hitResult: BlockHitResult): Boolean {
        val blacklist = listOf(
            Blocks.CHEST,
            Blocks.ENDER_CHEST,
            Blocks.FURNACE,
            Blocks.CRAFTING_TABLE,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.BARREL,
            Blocks.BEACON,
            Blocks.BLAST_FURNACE,
            Blocks.BREWING_STAND,
            Blocks.COMMAND_BLOCK,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.HOPPER,
            Blocks.GRINDSTONE,
            Blocks.LECTERN,
            Blocks.LOOM,
            Blocks.TRAPPED_CHEST,
            Blocks.SMITHING_TABLE,
            Blocks.SMOKER,
            Blocks.ENCHANTING_TABLE
        )
        val block = world.getBlockState(hitResult.blockPos).block
        return block !in blacklist
    }

    private fun handleBackpackInteraction(player: PlayerEntity, world: World, hand: Hand): Boolean {
        if (world.isClient) return false

        val stack = player.getStackInHand(hand)
        if (stack.item != Items.PLAYER_HEAD) return false

        LegacyMigration.migrateItemStack(stack)
        val backpackUUID = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.getUuid("BackpackUUID") ?: return false

        val backpack = BackpackCache[backpackUUID]
        if (backpack != null) InventoryHandler.openBackpack(player, backpack)

        return true
    }

    private fun getOrCreateRootBackpackFolder(server: MinecraftServer): File {
        val worldDir = server.getSavePath(WorldSavePath.ROOT).toFile()
        val rootFile = File(worldDir, "/backpacks/")
        if (!rootFile.exists()) {
            rootFile.mkdirs()
        }
        return rootFile
    }

}
