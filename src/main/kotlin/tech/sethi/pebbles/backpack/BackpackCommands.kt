package tech.sethi.pebbles.backpack

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.minecraft.command.CommandSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtHelper
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.io.File
import net.minecraft.util.WorldSavePath


object BackpackCommands {
    val backpacks = mutableMapOf<Int, BackpackInventory>()

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val padminCommand = CommandManager.literal("padmin").requires { source ->
            val player = source.player as? PlayerEntity
            player != null && (source.hasPermissionLevel(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
                player.uuid
            )!!.cachedData.permissionData.checkPermission("pebbles.admin").asBoolean()) || source.entity == null
        }

        val createBackpackCommand = CommandManager.literal("bp").then(CommandManager.literal("create")
            .then(CommandManager.argument("tier", StringArgumentType.word()).suggests { _, builder ->
                builder.suggest("leather").suggest("copper").suggest("iron").suggest("gold").suggest("diamond")
                    .suggest("netherite").buildFuture()
            }.then(CommandManager.argument("playerName", StringArgumentType.string()).suggests { context, builder ->
                CommandSource.suggestMatching(
                    context.source.server.playerManager.playerList.map { it.name.string }, builder
                )
            }.executes { ctx ->
                val nextId = (backpacks.keys.maxOrNull() ?: 0) + 1
                val playerName = StringArgumentType.getString(ctx, "playerName")
                val playerEntity = ctx.source.server.playerManager.getPlayer(playerName)

                if (playerEntity == null) {
                    ctx.source.sendError(Text.literal("Player '$playerName' not found."))
                    return@executes 0
                }

                val tier = StringArgumentType.getString(ctx, "tier")
                val size = when (tier) {
                    "leather" -> 9
                    "copper" -> 18
                    "iron" -> 27
                    "gold" -> 36
                    "diamond" -> 45
                    "netherite" -> 54
                    else -> 9
                }
                val skullStack = ItemStack(Items.PLAYER_HEAD)

                val backpackNbt = NbtHelper.fromNbtProviderString(getBackpackNbt(tier))
                skullStack.nbt = backpackNbt

                val skullMeta = skullStack.orCreateNbt
                skullMeta.putInt("BackpackID", nextId)

                playerEntity.giveItemStack(skullStack)

                val backpackInventory = BackpackInventory(size)
                backpackInventory.playerName = playerName // Set the playerName property
                backpacks[nextId] = backpackInventory

                ctx.source.sendFeedback(
                    { Text.literal("Backpack created with ID: $nextId for $playerName") }, false
                )

                val backpacksFile = getBackpacksFile(ctx.source.server)
                val player = ctx.source.server.playerManager.getPlayer(playerName)
                if (player != null) {
                    saveBackpacksData(backpacksFile)
                }

                return@executes 1
            })
            )
        )

        val getBackpackCommand = CommandManager.literal("bp").then(
            CommandManager.literal("get")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1)).suggests { _, builder ->
                    CommandSource.suggestMatching(backpacks.keys.map { it.toString() }, builder)
                }.executes { ctx ->
                    val id = IntegerArgumentType.getInteger(ctx, "id")
                    val playerEntity = ctx.source.player as? PlayerEntity

                    //get all backpack IDs
                    val backpackIds = backpacks.keys
                    if (!backpackIds.contains(id)) {
                        ctx.source.sendError(Text.literal("Backpack with ID $id not found."))
                        return@executes 0
                    }

                    if (playerEntity == null) {
                        ctx.source.sendError(Text.literal("Invalid player"))
                        return@executes 0
                    }

                    val tier = when (backpacks[id]?.getRows()) {
                        1 -> "leather"
                        2 -> "copper"
                        3 -> "iron"
                        4 -> "gold"
                        5 -> "diamond"
                        6 -> "netherite"
                        else -> "leather"
                    }
                    val size = when (tier) {
                        "leather" -> 9
                        "copper" -> 18
                        "iron" -> 27
                        "gold" -> 36
                        "diamond" -> 45
                        "netherite" -> 54
                        else -> 9
                    }

                    val skullStack = ItemStack(Items.PLAYER_HEAD)

                    val backpackNbt = NbtHelper.fromNbtProviderString(getBackpackNbt(tier))
                    skullStack.nbt = backpackNbt

                    val skullMeta = skullStack.orCreateNbt
                    skullMeta.putInt("BackpackID", id)

                    playerEntity.giveItemStack(skullStack)


                    ctx.source.sendFeedback(
                        { Text.literal("Backpack retrieved with: $id") }, false
                    )

                    return@executes 1
                })
        )



        padminCommand.then(getBackpackCommand)


        val openBackpackCommand = CommandManager.literal("bp").then(CommandManager.argument(
            "id", IntegerArgumentType.integer(1)
        ).executes { ctx ->
            val id = IntegerArgumentType.getInteger(ctx, "id")
            if (openBackpack(ctx.source.player!!, id)) {
                return@executes 1
            } else {
                ctx.source.sendError(Text.literal("Backpack with ID $id not found."))
                return@executes 0
            }
        })

        padminCommand.then(createBackpackCommand)
        padminCommand.then(openBackpackCommand)

        dispatcher.register(padminCommand)
    }

    private fun getBackpackTier(rowSize: Int): String {
        return when (rowSize) {
            1 -> "leather"
            2 -> "copper"
            3 -> "iron"
            4 -> "gold"
            5 -> "diamond"
            6 -> "netherite"
            else -> "leather"
        }
    }


    fun getBackpacksFile(server: MinecraftServer): File {
        val worldDir = server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(worldDir, "pebbles_backpacks.json")
    }

    private val gson = GsonBuilder().registerTypeAdapter(ItemStack::class.java, ItemStackTypeAdapter()).create()

    fun saveBackpacksData(file: File) {
        val backpacksData = backpacks.map { (id, backpackInventory) ->
            backpackInventory.toData(id)
        }

        file.writer().use { writer ->
            gson.toJson(backpacksData, writer)
        }
    }


    fun loadBackpacksData(file: File): Map<Int, BackpackInventory> {
        val backpackInventories = mutableMapOf<Int, BackpackInventory>()

        file.reader().use { reader ->
            val jsonElement = JsonParser.parseReader(reader)
            var backpackDataArray = JsonArray()
            if (!jsonElement.isJsonNull) {
                backpackDataArray = jsonElement.asJsonArray
            }

            for (backpackDataJson in backpackDataArray) {
                val backpackData = gson.fromJson(backpackDataJson, BackpackData::class.java)
                val backpackInventory = BackpackInventory(backpackData.size)

                // Load the ItemStacks into the BackpackInventory
                backpackData.items.forEachIndexed { index, itemStack ->
                    backpackInventory.setStack(index, itemStack)
                }

                backpackInventories[backpackData.id] = backpackInventory
            }
        }

        // Debug print
        println("Loaded backpacks data: $backpackInventories")

        return backpackInventories
    }

    fun openBackpack(player: PlayerEntity, id: Int): Boolean {
        val backpack = backpacks[id]
        val backpackTier = backpack?.getRows()
        // get names based on tier
        val backpackName = when (backpackTier) {
            1 -> "Leather"
            2 -> "Copper"
            3 -> "Iron"
            4 -> "Gold"
            5 -> "Diamond"
            6 -> "Netherite"
            else -> "Leather"
        }
        return if (backpack != null) {
            val rows = backpack.getRows()
            player.openHandledScreen(
                SimpleNamedScreenHandlerFactory(
                    { syncId, inv, _ ->
                        BackpackScreenHandler(syncId, inv, backpack, rows)
                    }, Text.literal("$backpackName Backpack").formatted(Formatting.DARK_RED)
                )
            )
            true
        } else {
            false
        }
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


    private fun getBackpackNbt(tier: String): String {
        return when (tier) {
            "leather" -> "{display:{Name:\"{\\\"text\\\":\\\"Leather Backpack\\\"}\"},SkullOwner:{Id:[I;-1865738760,-355187999,-1172757398,374987400],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDBiMWI1MzY3NDkxODM5MWEwN2E5ZDAwNTgyYzA1OGY5MjgwYmM1MjZhNzE2Yzc5NmVlNWVhYjRiZTEwYTc2MCJ9fX0=\"}]}}}"
            "copper" -> "{display:{Name:\"{\\\"text\\\":\\\"Copper Backpack\\\"}\"},SkullOwner:{Id:[I;1162937850,1879723887,-1267568232,-499049394],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWU1ODNjYjc3MTU4MWQzYjI3YjIzZjYxN2M3YjhhNDNkY2Q3MjIwNDQ3ZmY5NWZmMTk2MDQxNGQyMzUwYmRiOSJ9fX0=\"}]}}}"
            "iron" -> "{display:{Name:\"{\\\"text\\\":\\\"Iron Backpack\\\"}\"},SkullOwner:{Id:[I;1804696949,1735083680,-1716683629,-1934495154],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGRhZjhlZGMzMmFmYjQ2MWFlZTA3MTMwNTgwMjMxMDFmOTI0ZTJhN2VmYTg4M2RhZTcyZDVkNTdkNGMwNTNkNyJ9fX0=\"}]}}}"
            "gold" -> "{display:{Name:\"{\\\"text\\\":\\\"Gold Backpack\\\"}\"},SkullOwner:{Id:[I;1780200479,157369315,-1565115920,-961015289],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Y4NzUyNWFkODRlZmQxNjgwNmEyNmNhMDE5ODRiMjgwZTViYTY0MDM1MDViNmY2Yzk4MDNjMjQ2NDJhYmZjNyJ9fX0=\"}]}}}"
            "diamond" -> "{display:{Name:\"{\\\"text\\\":\\\"Diamond Backpack\\\"}\"},SkullOwner:{Id:[I;-104595003,-2052699552,-1909633784,2079891327],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTBkMWIwNzMyYmY3YTcwZGU0ZGMwMTU1OWNjNWM5ODExMDY4ZWY3YjYwOTUwMTAzODI3MDlmOTQwOTM5MjdmNiJ9fX0=\"}]}}}"
            "netherite" -> "{display:{Name:\"{\\\"text\\\":\\\"Netherite Backpack\\\"}\"},SkullOwner:{Id:[I;-814574281,-1699395768,-1993160043,-1564669232],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM1ZDdjYzA5ZmZmYmNhM2UxYzAwZDQyMWFmYWE0MzJjZjcxZmNiMDk1NTVmNTQ1MjNlNTIyMGQxYWYwZjk3ZCJ9fX0=\"}]}}}"
            "gucci" -> "{display:{Name:\"{\\\"text\\\":\\\"Gucci Backpack\\\"}\"},SkullOwner:{Id:[I;945208130,1552895596,-2057951394,2057894273],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTIwOGY1ODk3ZjEyZTVmY2IyZThiNDM4MWY1NDQ1YTc3MTFlODQ3MjFlYzRhN2ZjMTAxZDViNzQwYjg2ZjhmYSJ9fX0=\"}]}}}"
            else -> "{display:{Name:\"{\\\"text\\\":\\\"Bag\\\"}\"},SkullOwner:{Id:[I;-1980288287,-640760459,800809409,-1213206538],Properties:{textures:[{Value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzViMTE2ZGM3NjlkNmQ1NzI2ZjEyYTI0ZjNmMTg2ZjgzOTQyNzMyMWU4MmY0MTM4Nzc1YTRjNDAzNjdhNDkifX19\"}]}}}"
        }
    }
}
