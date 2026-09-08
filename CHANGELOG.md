# Create: Enchantment Industry Fly 2.5.2-p1

First build for Minecraft 26.2 and Create Fly 6.0.9. Requires a Create: Dragons Plus build for 26.2.

## Added

- Support for Minecraft 26.2 and Create Fly 6.0.9.

## Changed

- Blaze Enchanters and Blaze Forgers now accept liquid experience past their normal tank. The overflow fills the super experience tank, so a machine holds 8,000 mB in total instead of 4,000, and it can be filled either from a pipe or from an Experience Hatch. Note that this puts the machine into Super mode without charging a Super Experience Block with lightning.
- The Mechanical Grindstone and the Grindstone Drain can now disenchant Enchanting Templates, returning a blank template along with the experience. Templates were previously treated as unenchanted and passed through untouched.

## Fixed

- Fixed liquid experience only ever entering a machine once. A pump would deliver a single packet into a Printer, Blaze Enchanter, Blaze Forger or Experience Lantern and then stop permanently, leaving the machine stuck at a few mB while the source tank stayed full.
- Fixed an Experience Lantern crashing the server every time it ticked near a player above roughly level 15,400. On a dedicated server this disconnected everyone, not only the player holding the levels.
- Fixed the placed Printer's nozzle rendering detached from its body, with pieces vanishing depending on the camera angle. The item icon was never affected.
- Fixed the blaze hat floating away from its burner while a Blaze device was held in hand, most visible in first person.
