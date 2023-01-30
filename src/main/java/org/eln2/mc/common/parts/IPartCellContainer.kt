package org.eln2.mc.common.parts

import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider

interface IPartCellContainer {
    val cell : CellBase
    val provider : CellProvider

    val allowPlanarConnections : Boolean
    val allowInnerConnections : Boolean
    val allowWrappedConnections : Boolean
}
