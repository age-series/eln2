package org.eln2.mc.common.blocks.cell

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.cell.CellRegistry

class ResistorCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.RESISTOR_CELL.id
    }
}
