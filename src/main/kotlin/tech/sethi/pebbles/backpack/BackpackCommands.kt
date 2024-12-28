package tech.sethi.pebbles.backpack

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.UuidArgumentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.collection.DefaultedList
import tech.sethi.pebbles.backpack.api.Backpack
import tech.sethi.pebbles.backpack.api.BackpackTier
import tech.sethi.pebbles.backpack.inventory.InventoryHandler
import tech.sethi.pebbles.backpack.migration.LegacyMigration
import tech.sethi.pebbles.backpack.storage.BackpackCache


object BackpackCommands {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val padminCommand = CommandManager.literal("padmin").requires { source ->
            val player = source.player as? PlayerEntity
            player != null && (source.hasPermissionLevel(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
                player.uuid
            )!!.cachedData.permissionData.checkPermission("pebbles.admin").asBoolean()) || source.entity == null
        }

        val createBackpackCommand = CommandManager.literal("bp")
            .then(
                CommandManager.literal("create")
                    .then(
                        CommandManager.argument("tier", StringArgumentType.word())
                            .suggests { _, builder ->
                                CommandSource.suggestMatching(BackpackTier.values().map { it.name }, builder)
                            }
                            .then(
                                CommandManager.argument("target", EntityArgumentType.players())
                                    .executes { ctx ->
                                        val target = EntityArgumentType.getPlayer(ctx, "target")
                                        val tier = BackpackTier.valueOf(StringArgumentType.getString(ctx, "tier"))

                                        val backpack = Backpack(tier = tier, items = DefaultedList.ofSize(tier.size, ItemStack.EMPTY))
                                        BackpackCache[backpack.uuid] = backpack
                                        BackpackCache.saveAsync(backpack.uuid)

                                        target.giveItemStack(backpack.toItemStack())

                                        ctx.source.sendFeedback(
                                            { Text.literal("Backpack created with uuid ${backpack.uuid} for ${target.name}") }, false
                                        )

                                        return@executes 1
                                    })
                    )
            )

        val getBackpackCommand = CommandManager.literal("bp")
            .then(
                CommandManager.literal("get")
                    .then(
                        CommandManager.argument("uuid", UuidArgumentType.uuid())
                            .suggests { _, builder ->
                                CommandSource.suggestMatching(BackpackCache.keys.map { it.toString() }, builder)
                            }
                            .executes { ctx ->
                                val uuid = UuidArgumentType.getUuid(ctx, "uuid")
                                val player = ctx.source.playerOrThrow

                                val backpack = BackpackCache[uuid]

                                if (backpack == null) {
                                    ctx.source.sendError(Text.literal("Backpack with uuid $uuid not found."))
                                    return@executes 0
                                }

                                player.giveItemStack(backpack.toItemStack())
                                ctx.source.sendFeedback(
                                    { Text.literal("Backpack retrieved with uuid $uuid") }, false
                                )

                                return@executes 1
                            })
            )

        val openBackpackCommand = CommandManager.literal("bp")
            .then(
                CommandManager.literal("open")
                    .then(
                        CommandManager.argument("uuid", UuidArgumentType.uuid())
                            .suggests { _, builder ->
                                CommandSource.suggestMatching(BackpackCache.keys.map { it.toString() }, builder)
                            }
                            .executes { ctx ->
                                val uuid = UuidArgumentType.getUuid(ctx, "uuid")
                                val player = ctx.source.playerOrThrow
                                val backpack = BackpackCache[uuid]

                                if (backpack == null) {
                                    ctx.source.sendError(Text.literal("Backpack with uuid $uuid not found."))
                                    return@executes 0
                                }

                                InventoryHandler.openBackpack(player, backpack)
                                return@executes 1
                            }
                    ))

        padminCommand.then(getBackpackCommand)
        padminCommand.then(createBackpackCommand)
        padminCommand.then(openBackpackCommand)

        dispatcher.register(padminCommand)

        val getBackpackIdCommand = CommandManager.literal("backpack")
            .then(
                CommandManager.literal("id")
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        val itemInHand = player.getStackInHand(Hand.MAIN_HAND)

                        if (LegacyMigration.isBackpack(itemInHand)) {
                            LegacyMigration.migrateItemStack(itemInHand)
                            if (itemInHand.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.containsUuid("BackpackUUID") == true) {
                                val backpackUUID = itemInHand.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.getUuid("BackpackUUID")
                                ctx.source.sendFeedback({ Text.literal(backpackUUID.toString()) }, false)
                                return@executes 1
                            }
                        }

                        ctx.source.sendError(Text.literal("You are not currently holding a backpack."))
                        return@executes 0
                    }
            )

        dispatcher.register(getBackpackIdCommand)
    }

    private fun isLuckPermsPresent(): Boolean {
        return try {
            Class.forName("net.luckperms.api.LuckPerms")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun getLuckPermsApi(): LuckPerms? {
        return try {
            LuckPermsProvider.get()
        } catch (e: IllegalStateException) {
            null
        }
    }
}
