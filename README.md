# Pebble's Backpack
Fully server-sided shareable backpack mod made for Fabric! Create different tiers of backpacks including Leather, Copper, Iron, Gold, Diamond and Netherite!

The mod is still in its early stages so if you run into any issue please let me know. Appreciate any and all feedback/bug reports.

<p><a title="Fabric Language Kotlin" href="https://modrinth.com/mod/fabric-language-kotlin" target="_blank" rel="noopener noreferrer"><img style="display: block; margin-left: auto; margin-right: auto;" src="https://i.imgur.com/c1DH9VL.png" alt="" width="171" height="50" /></a></p>

The backpack mod allows for users to store data on the go and use it at the same time as other people that has the backpack with the same ID.

# LuckPerm Permission node
`pebbles.admin`: For all admin backpack commands. There are no commands for an average joe on the server, the backpacks are a physical item.
<br>
<br>
# Commands:
`/padmin bp create TIER PLAYERNAME`: Create a backpack for a specific player. Tiers includes: `leather (3x3), copper (9x2), iron (9x3), gold (9x4), diamond (9x5), netherite (9x6)` <br>
<br>
Special thanks to Minecraft-heads for hosting these skins: https://minecraft-heads.com/<br>
<br>
`/padmin bp ID`: View backpack with the specific ID<br>
`/padmin bp get ID`: Get a copy of the backpack that can be used together at the same time. Great for players who want to share their inventory<br>
 
The backpack data is stored in `/world/pebbles_backpacks.json`


Current limitations:
- Cannot right click drag items around the backpack.
- Backpack owner names aren't stored properly yet (still accessible through backpack ID)
- Minor optimization still on the way
